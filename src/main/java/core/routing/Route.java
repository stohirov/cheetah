package core.routing;

import http.HttpMethod;
import http.HttpRequest;
import http.HttpResponse;
import http.HttpStatus;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import json.Json;

public final class Route {

  private final HttpMethod httpMethod;
  private final PathPattern pattern;
  private final String consumes;
  private final String produces;
  private final Object controller;
  private final Method method;
  private final List<ArgumentBinder> binders;

  Route(HttpMethod httpMethod, PathPattern pattern, String consumes, String produces,
      Object controller, Method method) {
    this.httpMethod = httpMethod;
    this.pattern = pattern;
    this.consumes = consumes;
    this.produces = produces;
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

    Object result;
    try {
      result = method.invoke(controller, arguments);
    } catch (InvocationTargetException e) {
      if (e.getCause() instanceof Exception cause) {
        throw cause;
      }

      throw (Error) e.getCause();
    }

    return toResponse(result);
  }

  @Override
  public String toString() {
    return httpMethod + " " + pattern + " -> "
        + method.getDeclaringClass().getSimpleName() + "." + method.getName();
  }

  private HttpResponse toResponse(Object result) {
    if (result instanceof HttpResponse response) {
      return response;
    }

    if (result == null) {
      return HttpResponse.of(HttpStatus.NO_CONTENT);
    }

    HttpResponse response;
    if (result instanceof byte[] bytes) {
      response = new HttpResponse(HttpStatus.OK,
          Map.of("Content-Type", "application/octet-stream"), bytes);
    } else if (result instanceof CharSequence text) {
      response = HttpResponse.text(HttpStatus.OK, text.toString());
    } else {
      response = HttpResponse.json(HttpStatus.OK, Json.write(result));
    }

    return produces.isBlank() ? response : response.withHeader("Content-Type", produces);
  }

  private static String mediaType(String contentType) {
    int separator = contentType.indexOf(';');

    return (separator < 0 ? contentType : contentType.substring(0, separator)).trim();
  }

}
