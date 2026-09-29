package core.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import annotations.Controller;
import annotations.DeleteMethod;
import annotations.GetMethod;
import annotations.PathVariable;
import annotations.PostMethod;
import annotations.ReqBody;
import annotations.ReqParam;
import http.HttpException;
import http.HttpMethod;
import http.HttpRequest;
import http.HttpResponse;
import http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RouterTest {

  record Item(long id, String name) {
  }

  @Controller(path = "/items")
  static class ItemController {

    @GetMethod(path = "/{id}")
    Item get(@PathVariable long id) {
      return new Item(id, "item-" + id);
    }

    @GetMethod(path = "/latest")
    String latest() {
      return "latest";
    }

    @GetMethod
    List<Item> search(@ReqParam(value = "q") String query, @ReqParam Optional<Integer> limit) {
      return List.of(new Item(limit.orElse(1), query));
    }

    @PostMethod(consumes = "application/json")
    HttpResponse create(@ReqBody Item item) {
      return HttpResponse.json(HttpStatus.CREATED, "{\"id\":" + item.id() + "}");
    }

    @DeleteMethod(path = "/{id}")
    void delete(@PathVariable("id") long id) {
    }

    @GetMethod(path = "/{id}/raw", produces = "text/csv")
    String raw(@PathVariable long id, HttpRequest request) {
      return id + "," + request.path();
    }
  }

  static class BrokenController {

    @GetMethod(path = "/broken/{id}")
    String broken(@PathVariable long other) {
      return "";
    }
  }

  static class DuplicateController {

    @GetMethod(path = "/items/{name}")
    String duplicate(@PathVariable String name) {
      return name;
    }
  }

  private final Router router = new Router().register(new ItemController());

  @Test
  void bindsPathVariablesAndWritesJson() throws Exception {
    HttpResponse response = router.handle(request(HttpMethod.GET, "/items/5"));

    assertEquals(HttpStatus.OK, response.status());
    assertEquals(Optional.of(HttpResponse.APPLICATION_JSON), response.header("content-type"));
    assertEquals("{\"id\":5,\"name\":\"item-5\"}", body(response));
  }

  @Test
  void prefersLiteralSegmentsOverVariables() throws Exception {
    assertEquals("latest", body(router.handle(request(HttpMethod.GET, "/items/latest"))));
  }

  @Test
  void bindsQueryParameters() throws Exception {
    HttpResponse response = router.handle(request(HttpMethod.GET, "/items/", Map.of(
        "q", List.of("pen"), "limit", List.of("3")), Map.of(), ""));

    assertEquals("[{\"id\":3,\"name\":\"pen\"}]", body(response));
  }

  @Test
  void rejectsMissingRequiredQueryParameter() {
    HttpException exception = assertThrows(HttpException.class,
        () -> router.handle(request(HttpMethod.GET, "/items")));

    assertEquals(HttpStatus.BAD_REQUEST, exception.status());
  }

  @Test
  void rejectsUnconvertiblePathVariable() {
    HttpException exception = assertThrows(HttpException.class,
        () -> router.handle(request(HttpMethod.DELETE, "/items/abc")));

    assertEquals(HttpStatus.BAD_REQUEST, exception.status());
  }

  @Test
  void readsJsonBody() throws Exception {
    HttpResponse response = router.handle(request(HttpMethod.POST, "/items", Map.of(),
        Map.of("content-type", "application/json; charset=utf-8"), "{\"id\":9,\"name\":\"x\"}"));

    assertEquals(HttpStatus.CREATED, response.status());
    assertEquals("{\"id\":9}", body(response));
  }

  @Test
  void rejectsInvalidJsonBody() {
    HttpException exception = assertThrows(HttpException.class,
        () -> router.handle(request(HttpMethod.POST, "/items", Map.of(),
            Map.of("content-type", "application/json"), "{\"id\":\"x\"}")));

    assertEquals(HttpStatus.BAD_REQUEST, exception.status());
  }

  @Test
  void rejectsWrongContentType() {
    HttpException exception = assertThrows(HttpException.class,
        () -> router.handle(request(HttpMethod.POST, "/items", Map.of(),
            Map.of("content-type", "text/plain"), "{}")));

    assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, exception.status());
  }

  @Test
  void returnsNoContentForVoid() throws Exception {
    assertEquals(HttpStatus.NO_CONTENT,
        router.handle(request(HttpMethod.DELETE, "/items/1")).status());
  }

  @Test
  void appliesProducesAndInjectsRequest() throws Exception {
    HttpResponse response = router.handle(request(HttpMethod.GET, "/items/2/raw"));

    assertEquals(Optional.of("text/csv"), response.header("Content-Type"));
    assertEquals("2,/items/2/raw", body(response));
  }

  @Test
  void reportsUnknownPath() {
    HttpException exception = assertThrows(HttpException.class,
        () -> router.handle(request(HttpMethod.GET, "/nope")));

    assertEquals(HttpStatus.NOT_FOUND, exception.status());
  }

  @Test
  void reportsDisallowedMethod() {
    HttpException exception = assertThrows(HttpException.class,
        () -> router.handle(request(HttpMethod.PUT, "/items/1")));

    assertEquals(HttpStatus.METHOD_NOT_ALLOWED, exception.status());
    assertEquals(Map.of("Allow", "DELETE, GET"), exception.headers());
  }

  @Test
  void failsFastOnUnknownPathVariable() {
    assertThrows(IllegalArgumentException.class,
        () -> new Router().register(new BrokenController()));
  }

  @Test
  void failsFastOnDuplicateRoute() {
    assertThrows(IllegalStateException.class,
        () -> router.register(new DuplicateController()));
  }

  private static HttpRequest request(HttpMethod method, String path) {
    return request(method, path, Map.of(), Map.of(), "");
  }

  private static HttpRequest request(HttpMethod method, String path,
      Map<String, List<String>> query, Map<String, String> headers, String body) {
    return new HttpRequest(method, path, "HTTP/1.1", query, headers,
        body.getBytes(StandardCharsets.UTF_8));
  }

  private static String body(HttpResponse response) {
    return new String(response.body(), StandardCharsets.UTF_8);
  }

}
