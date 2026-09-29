package core.routing;

import annotations.Controller;
import annotations.DeleteMethod;
import annotations.GetMethod;
import annotations.PostMethod;
import annotations.PutMethod;
import core.handler.Handler;
import http.HttpException;
import http.HttpMethod;
import http.HttpRequest;
import http.HttpResponse;
import http.HttpStatus;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class Router implements Handler {

  private final List<Route> routes = new ArrayList<>();

  public Router register(Object controller) {
    Class<?> type = controller.getClass();
    Controller annotation = type.getAnnotation(Controller.class);
    String prefix = annotation == null ? "" : annotation.path();

    List<Method> methods = Arrays.stream(type.getDeclaredMethods())
        .sorted(Comparator.comparing(Method::getName))
        .toList();

    for (Method method : methods) {
      mappingOf(method).ifPresent(mapping -> add(new Route(
          mapping.httpMethod(),
          PathPattern.compile(prefix + "/" + mapping.path()),
          mapping.consumes(),
          mapping.produces(),
          controller,
          method)));
    }

    return this;
  }

  public List<Route> routes() {
    return List.copyOf(routes);
  }

  @Override
  public HttpResponse handle(HttpRequest request) throws Exception {
    List<RouteMatch> matches = new ArrayList<>();
    for (Route route : routes) {
      route.pattern().match(request.path())
          .ifPresent(variables -> matches.add(new RouteMatch(route, variables)));
    }

    if (matches.isEmpty()) {
      throw new HttpException(HttpStatus.NOT_FOUND, "No route for " + request.path());
    }

    Optional<RouteMatch> match = matches.stream()
        .filter(candidate -> candidate.route().httpMethod() == request.method())
        .min((first, second) -> first.route().pattern()
            .compareSpecificity(second.route().pattern()));

    if (match.isEmpty()) {
      String allowed = matches.stream()
          .map(candidate -> candidate.route().httpMethod().name())
          .distinct()
          .sorted()
          .collect(Collectors.joining(", "));

      throw new HttpException(HttpStatus.METHOD_NOT_ALLOWED,
          request.method() + " is not allowed for " + request.path())
          .withHeader("Allow", allowed);
    }

    Route route = match.get().route();
    if (!route.accepts(request)) {
      throw new HttpException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
          "Expected Content-Type " + route.consumes());
    }

    return route.invoke(request, match.get().pathVariables());
  }

  private void add(Route route) {
    for (Route existing : routes) {
      boolean sameMethod = existing.httpMethod() == route.httpMethod();
      if (sameMethod && existing.pattern().overlaps(route.pattern())) {
        throw new IllegalStateException("Duplicate route: " + route + " conflicts with "
            + existing);
      }
    }

    routes.add(route);
  }

  private static Optional<RouteMapping> mappingOf(Method method) {
    List<RouteMapping> mappings = new ArrayList<>();

    GetMethod get = method.getAnnotation(GetMethod.class);
    if (get != null) {
      mappings.add(new RouteMapping(HttpMethod.GET, get.path(), get.consumes(), get.produces()));
    }

    PostMethod post = method.getAnnotation(PostMethod.class);
    if (post != null) {
      mappings.add(
          new RouteMapping(HttpMethod.POST, post.path(), post.consumes(), post.produces()));
    }

    PutMethod put = method.getAnnotation(PutMethod.class);
    if (put != null) {
      mappings.add(new RouteMapping(HttpMethod.PUT, put.path(), put.consumes(), put.produces()));
    }

    DeleteMethod delete = method.getAnnotation(DeleteMethod.class);
    if (delete != null) {
      mappings.add(new RouteMapping(
          HttpMethod.DELETE, delete.path(), delete.consumes(), delete.produces()));
    }

    if (mappings.size() > 1) {
      throw new IllegalArgumentException(
          method.getName() + " has more than one HTTP method annotation");
    }

    return mappings.stream().findFirst();
  }

  private record RouteMapping(HttpMethod httpMethod, String path, String consumes,
      String produces) {
  }

  private record RouteMatch(Route route, Map<String, String> pathVariables) {
  }

}
