package core.context;

import annotations.Value;
import config.Config;
import convert.StringConverter;
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
import java.util.stream.Collectors;

public class ApplicationContext {

  private final Config config;
  private final List<Class<?>> componentTypes;
  private final Map<Class<?>, Object> instances = new HashMap<>();
  private final Set<Class<?>> creating = new LinkedHashSet<>();

  public ApplicationContext(Config config, Collection<Class<?>> componentTypes) {
    this.config = config;
    this.componentTypes = List.copyOf(new LinkedHashSet<>(componentTypes));
  }

  public synchronized void refresh() {
    for (Class<?> type : componentTypes) {
      instance(type);
    }
  }

  public Config config() {
    return config;
  }

  public synchronized <T> T getBean(Class<T> type) {
    return type.cast(instance(resolve(type)));
  }

  public synchronized <T> List<T> getBeansOfType(Class<T> type) {
    return componentTypes.stream()
        .filter(type::isAssignableFrom)
        .map(candidate -> type.cast(instance(candidate)))
        .toList();
  }

  public synchronized List<Object> getBeansWithAnnotation(Class<? extends Annotation> annotation) {
    return componentTypes.stream()
        .filter(candidate -> candidate.isAnnotationPresent(annotation))
        .map(this::instance)
        .toList();
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
    Object existing = instances.get(type);
    if (existing != null) {
      return existing;
    }

    if (!creating.add(type)) {
      String chain = creating.stream()
          .map(Class::getSimpleName)
          .collect(Collectors.joining(" -> "));

      throw new BeanException("Circular dependency: " + chain + " -> " + type.getSimpleName());
    }

    try {
      Object created = create(type);
      instances.put(type, created);

      return created;
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

    if (type == List.class) {
      return getBeansOfType(listElementType(parameter, owner));
    }

    return instance(resolve(type));
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

  private static Class<?> listElementType(Parameter parameter, Class<?> owner) {
    if (parameter.getParameterizedType() instanceof ParameterizedType parameterized
        && parameterized.getActualTypeArguments()[0] instanceof Class<?> element) {
      return element;
    }

    throw new BeanException("List parameters of " + owner.getSimpleName()
        + " need a concrete element type");
  }

}
