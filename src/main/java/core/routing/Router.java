package core.routing;

import annotations.Controller;
import annotations.DeleteMethod;
import annotations.GetMethod;
import annotations.PatchMethod;
import annotations.PostMethod;
import annotations.PutMethod;
import core.handler.Handler;
import http.HttpException;
import http.HttpMethod;
import http.HttpRequest;
import http.HttpResponse;
import http.HttpStatus;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

public class Router implements Handler {

  private static final Map<Class<? extends Annotation>, HttpMethod> METHOD_ANNOTATIONS =
      Map.of(
          GetMethod.class, HttpMethod.GET,
          PostMethod.class, HttpMethod.POST,
          PutMethod.class, HttpMethod.PUT,
          PatchMethod.class, HttpMethod.PATCH,
          DeleteMethod.class, HttpMethod.DELETE);

  private final List<Route> routes = new ArrayList<>();

  public Router register(Object controller) {
    return register(controller.getClass(), () -> controller);
  }

  public Router register(Class<?> type, Supplier<?> controller) {
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
          mapping.status(),
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

    String allowed = allowedMethods(matches);
    if (request.method() == HttpMethod.OPTIONS) {
      return HttpResponse.of(HttpStatus.NO_CONTENT).withHeader("Allow", allowed);
    }

    HttpMethod method = request.method() == HttpMethod.HEAD ? HttpMethod.GET : request.method();
    Optional<RouteMatch> match = matches.stream()
        .filter(candidate -> candidate.route().httpMethod() == method)
        .min((first, second) -> first.route().pattern()
            .compareSpecificity(second.route().pattern()));

    if (match.isEmpty()) {
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

  public boolean hasRoute(String path) {
    return routes.stream().anyMatch(route -> route.pattern().match(path).isPresent());
  }

  private static String allowedMethods(List<RouteMatch> matches) {
    Set<String> allowed = new TreeSet<>();
    for (RouteMatch match : matches) {
      allowed.add(match.route().httpMethod().name());
    }

    if (allowed.contains(HttpMethod.GET.name())) {
      allowed.add(HttpMethod.HEAD.name());
    }

    allowed.add(HttpMethod.OPTIONS.name());

    return String.join(", ", allowed);
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

    for (Map.Entry<Class<? extends Annotation>, HttpMethod> entry : METHOD_ANNOTATIONS.entrySet()) {
      Annotation annotation = method.getAnnotation(entry.getKey());
      if (annotation != null) {
        mappings.add(RouteMapping.of(entry.getValue(), annotation));
      }
    }

    if (mappings.size() > 1) {
      throw new IllegalArgumentException(
          method.getName() + " has more than one HTTP method annotation");
    }

    return mappings.stream().findFirst();
  }

  private record RouteMapping(HttpMethod httpMethod, String path, String consumes,
      String produces, HttpStatus status) {

    static RouteMapping of(HttpMethod httpMethod, Annotation annotation) {
      return new RouteMapping(httpMethod,
          (String) attribute(annotation, "path"),
          (String) attribute(annotation, "consumes"),
          (String) attribute(annotation, "produces"),
          (HttpStatus) attribute(annotation, "status"));
    }

    private static Object attribute(Annotation annotation, String name) {
      try {
        return annotation.annotationType().getMethod(name).invoke(annotation);
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("Cannot read " + name + " of " + annotation, e);
      }
    }
  }

  private record RouteMatch(Route route, Map<String, String> pathVariables) {
  }

}
