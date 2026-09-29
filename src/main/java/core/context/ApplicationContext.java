package core.context;

import annotations.RequestScoped;
import annotations.Value;
import config.Config;
import convert.StringConverter;
import http.HttpRequest;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ApplicationContext {

  private final Config config;
  private final List<Class<?>> componentTypes;
  private final Map<Class<?>, Object> singletons = new HashMap<>();
  private final Set<Class<?>> creating = new LinkedHashSet<>();

  public ApplicationContext(Config config, Collection<Class<?>> componentTypes) {
    this.config = config;
    this.componentTypes = List.copyOf(new LinkedHashSet<>(componentTypes));
  }

  public synchronized void refresh() {
    for (Class<?> type : componentTypes) {
      if (isRequestScoped(type)) {
        verifyDependencies(type);
      } else {
        instance(type);
      }
    }
  }

  public Config config() {
    return config;
  }

  public static boolean isRequestScoped(Class<?> type) {
    return type.isAnnotationPresent(RequestScoped.class);
  }

  public synchronized <T> T getBean(Class<T> type) {
    return type.cast(instance(resolve(type)));
  }

  public synchronized <T> List<T> getBeansOfType(Class<T> type) {
    return componentTypes.stream()
        .filter(type::isAssignableFrom)
        .filter(candidate -> !isRequestScoped(candidate))
        .map(candidate -> type.cast(instance(candidate)))
        .toList();
  }

  public List<Class<?>> typesWithAnnotation(Class<? extends Annotation> annotation) {
    return componentTypes.stream()
        .filter(candidate -> candidate.isAnnotationPresent(annotation))
        .toList();
  }

  public List<Class<?>> componentTypes() {
    return componentTypes;
  }

  public synchronized Supplier<Object> beanSupplier(Class<?> type) {
    if (isRequestScoped(type)) {
      return () -> getBean(type);
    }

    Object singleton = instance(type);

    return () -> singleton;
  }

  private Class<?> resolve(Class<?> type) {
    List<Class<?>> candidates = componentTypes.stream().filter(type::isAssignableFrom).toList();

    if (candidates.isEmpty()) {
      throw new BeanException("No component of type " + type.getName());
    }

    if (candidates.size() > 1) {
      throw new BeanException("Multiple components of type " + type.getName() + ": "
          + candidates.stream().map(Class::getSimpleName).collect(Collectors.joining(", ")));
    }

    return candidates.get(0);
  }

  private Object instance(Class<?> type) {
    if (!isRequestScoped(type)) {
      Object existing = singletons.get(type);
      if (existing != null) {
        return existing;
      }

      Object created = createTracked(type);
      singletons.put(type, created);

      return created;
    }

    RequestContext request = RequestContext.find().orElseThrow(() -> new BeanException(
        type.getSimpleName() + " is request scoped but no request is active"));

    Object existing = request.bean(type);
    if (existing != null) {
      return existing;
    }

    Object created = createTracked(type);
    request.putBean(type, created);

    return created;
  }

  private Object createTracked(Class<?> type) {
    if (!creating.add(type)) {
      String chain = creating.stream()
          .map(Class::getSimpleName)
          .collect(Collectors.joining(" -> "));

      throw new BeanException("Circular dependency: " + chain + " -> " + type.getSimpleName());
    }

    try {
      return create(type);
    } finally {
      creating.remove(type);
    }
  }

  private Object create(Class<?> type) {
    Constructor<?> constructor = selectConstructor(type);
    Parameter[] parameters = constructor.getParameters();

    Object[] arguments = new Object[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
      arguments[i] = resolveArgument(parameters[i], type);
    }

    try {
      constructor.setAccessible(true);

      return constructor.newInstance(arguments);
    } catch (InvocationTargetException e) {
      throw new BeanException("Failed to create " + type.getName(), e.getCause());
    } catch (ReflectiveOperationException e) {
      throw new BeanException("Cannot instantiate " + type.getName(), e);
    }
  }

  private void verifyDependencies(Class<?> type) {
    for (Parameter parameter : selectConstructor(type).getParameters()) {
      Value value = parameter.getAnnotation(Value.class);
      if (value != null) {
        resolveValue(value.value(), parameter.getType(), type);
        continue;
      }

      Class<?> dependency = parameter.getType();
      if (dependency == Provider.class || dependency == List.class) {
        typeArgument(parameter, type);
      } else if (!isBuiltIn(dependency)) {
        resolve(dependency);
      }
    }
  }

  private static boolean isBuiltIn(Class<?> type) {
    return type == Config.class || type == ApplicationContext.class || type == HttpRequest.class;
  }

  private static Constructor<?> selectConstructor(Class<?> type) {
    Constructor<?>[] constructors = type.getDeclaredConstructors();
    if (constructors.length == 1) {
      return constructors[0];
    }

    return Arrays.stream(constructors)
        .filter(constructor -> constructor.getParameterCount() == 0)
        .findFirst()
        .orElseThrow(() -> new BeanException(
            type.getName() + " must have a single constructor or a no-argument one"));
  }

  private Object resolveArgument(Parameter parameter, Class<?> owner) {
    Value value = parameter.getAnnotation(Value.class);
    if (value != null) {
      return resolveValue(value.value(), parameter.getType(), owner);
    }

    Class<?> type = parameter.getType();
    if (type == Config.class) {
      return config;
    }

    if (type == ApplicationContext.class) {
      return this;
    }

    if (type == HttpRequest.class) {
      requireRequestScoped(owner, "HttpRequest");

      return RequestContext.current().request();
    }

    if (type == List.class) {
      return getBeansOfType(typeArgument(parameter, owner));
    }

    if (type == Provider.class) {
      Class<?> provided = typeArgument(parameter, owner);

      return (Provider<Object>) () -> getBean(provided);
    }

    Class<?> dependency = resolve(type);
    if (isRequestScoped(dependency)) {
      requireRequestScoped(owner, dependency.getSimpleName()
          + " (inject Provider<" + type.getSimpleName() + "> instead)");
    }

    return instance(dependency);
  }

  private static void requireRequestScoped(Class<?> owner, String dependency) {
    if (!isRequestScoped(owner)) {
      throw new BeanException("Singleton " + owner.getSimpleName()
          + " cannot depend on request-scoped " + dependency);
    }
  }

  private Object resolveValue(String expression, Class<?> type, Class<?> owner) {
    int separator = expression.indexOf(':');
    String key = separator < 0 ? expression : expression.substring(0, separator);
    Optional<String> fallback = separator < 0
        ? Optional.empty()
        : Optional.of(expression.substring(separator + 1));

    String raw = config.get(key)
        .or(() -> fallback)
        .orElseThrow(() -> new BeanException(
            "Missing config property '" + key + "' required by " + owner.getSimpleName()));

    try {
      return StringConverter.convert(raw, type);
    } catch (IllegalArgumentException e) {
      throw new BeanException("Invalid config property '" + key + "' for "
          + owner.getSimpleName() + ": " + e.getMessage(), e);
    }
  }

  private static Class<?> typeArgument(Parameter parameter, Class<?> owner) {
    if (parameter.getParameterizedType() instanceof ParameterizedType parameterized
        && parameterized.getActualTypeArguments()[0] instanceof Class<?> element) {
      return element;
    }

    throw new BeanException("Generic parameters of " + owner.getSimpleName()
        + " need a concrete type argument");
  }

}
