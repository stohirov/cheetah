package core.error;

import annotations.ExceptionHandler;
import core.routing.ResponseConverter;
import http.HttpRequest;
import http.HttpResponse;
import http.HttpStatus;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class ExceptionHandlers {

  private final List<HandlerMethod> handlers = new ArrayList<>();

  public ExceptionHandlers register(Object bean) {
    return register(bean.getClass(), () -> bean);
  }

  public ExceptionHandlers register(Class<?> type, Supplier<?> bean) {
    List<Method> methods = Arrays.stream(type.getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(ExceptionHandler.class))
        .sorted(Comparator.comparing(Method::getName))
        .toList();

    for (Method method : methods) {
      ExceptionHandler annotation = method.getAnnotation(ExceptionHandler.class);
      List<Class<? extends Throwable>> types = handledTypes(method, annotation);

      method.setAccessible(true);
      handlers.add(new HandlerMethod(bean, method, types, annotation.status()));
    }

    return this;
  }

  public Optional<HttpResponse> handle(Exception exception, HttpRequest request)
      throws Exception {
    HandlerMethod best = null;
    int bestDistance = Integer.MAX_VALUE;

    for (HandlerMethod handler : handlers) {
      for (Class<? extends Throwable> type : handler.types()) {
        int distance = distance(exception.getClass(), type);
        if (distance < bestDistance) {
          best = handler;
          bestDistance = distance;
        }
      }
    }

    if (best == null) {
      return Optional.empty();
    }

    return Optional.of(best.invoke(exception, request));
  }

  private static List<Class<? extends Throwable>> handledTypes(
      Method method, ExceptionHandler annotation) {
    List<Class<? extends Throwable>> declared = List.of(annotation.value());
    List<Class<? extends Throwable>> fromParameters = new ArrayList<>();

    for (Parameter parameter : method.getParameters()) {
      Class<?> type = parameter.getType();

      if (Throwable.class.isAssignableFrom(type)) {
        fromParameters.add(type.asSubclass(Throwable.class));
      } else if (type != HttpRequest.class) {
        throw new IllegalArgumentException("Exception handler " + method.getName()
            + " may only take the exception and HttpRequest");
      }
    }

    List<Class<? extends Throwable>> types = declared.isEmpty() ? fromParameters : declared;
    if (types.isEmpty()) {
      throw new IllegalArgumentException(
          "Exception handler " + method.getName() + " does not declare an exception type");
    }

    for (Class<? extends Throwable> type : types) {
      for (Class<? extends Throwable> parameterType : fromParameters) {
        if (!parameterType.isAssignableFrom(type)) {
          throw new IllegalArgumentException("Exception handler " + method.getName()
              + " cannot receive " + type.getSimpleName() + " as "
              + parameterType.getSimpleName());
        }
      }
    }

    return types;
  }

  private static int distance(Class<?> exceptionType, Class<?> handledType) {
    int distance = 0;

    for (Class<?> current = exceptionType; current != null; current = current.getSuperclass()) {
      if (current == handledType) {
        return distance;
      }

      distance++;
    }

    return Integer.MAX_VALUE;
  }

  private record HandlerMethod(Supplier<?> bean, Method method,
      List<Class<? extends Throwable>> types, HttpStatus status) {

    HttpResponse invoke(Exception exception, HttpRequest request) throws Exception {
      Object[] arguments = Arrays.stream(method.getParameters())
          .map(parameter -> parameter.getType() == HttpRequest.class ? request : exception)
          .toArray();

      Object result = ResponseConverter.invoke(method, bean.get(), arguments);

      return ResponseConverter.convert(result, status, "");
    }
  }

}
