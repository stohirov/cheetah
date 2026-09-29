package core.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import annotations.Order;
import http.HttpMethod;
import http.HttpRequest;
import http.HttpResponse;
import http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FilteringHandlerTest {

  private final List<String> calls = new ArrayList<>();

  @Order(2)
  class Second implements Filter {

    @Override
    public HttpResponse filter(HttpRequest request, FilterChain chain) throws Exception {
      calls.add("second");

      return chain.next(request);
    }
  }

  @Order(1)
  class First implements Filter {

    @Override
    public HttpResponse filter(HttpRequest request, FilterChain chain) throws Exception {
      calls.add("first");

      return chain.next(request).withHeader("X-First", "yes");
    }
  }

  class Unordered implements Filter {

    @Override
    public HttpResponse filter(HttpRequest request, FilterChain chain) throws Exception {
      calls.add("unordered");

      return chain.next(request);
    }
  }

  @Test
  void runsFiltersInOrderAroundTarget() throws Exception {
    FilteringHandler handler = new FilteringHandler(
        List.of(new Second(), new Unordered(), new First()), request -> {
          calls.add("target");

          return HttpResponse.of(HttpStatus.OK);
        });

    HttpResponse response = handler.handle(request("/"));

    assertEquals(List.of("unordered", "first", "second", "target"), calls);
    assertEquals(Optional.of("yes"), response.header("X-First"));
  }

  @Test
  void allowsShortCircuitAndRequestRewrite() throws Exception {
    Filter guard = (request, chain) -> request.path().startsWith("/admin")
        ? HttpResponse.of(HttpStatus.FORBIDDEN)
        : chain.next(request);
    Filter rewrite = (request, chain) -> chain.next(new HttpRequest(request.method(),
        "/v1" + request.path(), request.version(), request.queryParams(), request.headers(),
        request.body()));

    FilteringHandler handler = new FilteringHandler(List.of(guard, rewrite),
        request -> HttpResponse.text(HttpStatus.OK, request.path()));

    assertEquals(HttpStatus.FORBIDDEN, handler.handle(request("/admin")).status());
    assertEquals("/v1/users", body(handler.handle(request("/users"))));
  }

  private static HttpRequest request(String path) {
    return new HttpRequest(HttpMethod.GET, path, "HTTP/1.1", Map.of(), Map.of(), new byte[0]);
  }

  private static String body(HttpResponse response) {
    return new String(response.body(), StandardCharsets.UTF_8);
  }

}
