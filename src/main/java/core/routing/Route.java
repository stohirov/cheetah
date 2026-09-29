package core.routing;

import http.HttpMethod;
import http.HttpRequest;
import http.HttpResponse;
import http.HttpStatus;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class Route {

  private final HttpMethod httpMethod;
  private final PathPattern pattern;
  private final String consumes;
  private final String produces;
  private final HttpStatus status;
  private final Supplier<?> controller;
  private final Method method;
  private final List<ArgumentBinder> binders;

  Route(HttpMethod httpMethod, PathPattern pattern, String consumes, String produces,
      HttpStatus status, Supplier<?> controller, Method method) {
    this.httpMethod = httpMethod;
    this.pattern = pattern;
    this.consumes = consumes;
    this.produces = produces;
    this.status = status;
    this.controller = controller;
    this.method = method;

    this.binders = Arrays.stream(method.getParameters())
        .map(parameter -> ArgumentBinder.forParameter(parameter, pattern))
        .toList();

    method.setAccessible(true);
  }

  public HttpMethod httpMethod() {
    return httpMethod;
  }

  public PathPattern pattern() {
    return pattern;
  }

  public String consumes() {
    return consumes;
  }

  boolean accepts(HttpRequest request) {
    if (consumes.isBlank()) {
      return true;
    }

    String contentType = request.header("content-type").orElse("");

    return mediaType(contentType).equalsIgnoreCase(mediaType(consumes));
  }

  HttpResponse invoke(HttpRequest request, Map<String, String> pathVariables) throws Exception {
    Object[] arguments = new Object[binders.size()];
    for (int i = 0; i < arguments.length; i++) {
      arguments[i] = binders.get(i).bind(request, pathVariables);
    }

    Object result = ResponseConverter.invoke(method, controller.get(), arguments);

    return ResponseConverter.convert(result, status, produces);
  }

  @Override
  public String toString() {
    return httpMethod + " " + pattern + " -> "
        + method.getDeclaringClass().getSimpleName() + "." + method.getName();
  }

  private static String mediaType(String contentType) {
    int separator = contentType.indexOf(';');

    return (separator < 0 ? contentType : contentType.substring(0, separator)).trim();
  }

}
