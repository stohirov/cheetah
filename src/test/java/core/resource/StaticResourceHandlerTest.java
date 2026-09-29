package core.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import annotations.GetMethod;
import core.routing.Router;
import http.HttpException;
import http.HttpMethod;
import http.HttpRequest;
import http.HttpResponse;
import http.HttpStatus;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StaticResourceHandlerTest {

  static class ApiController {

    @GetMethod(path = "/api")
    String api() {
      return "from route";
    }
  }

  @TempDir
  Path root;

  private URLClassLoader classLoader;
  private StaticResourceHandler handler;

  @BeforeEach
  void setUp() throws IOException {
    Files.createDirectories(root.resolve("static/css"));
    Files.createDirectories(root.resolve("static/docs"));
    Files.writeString(root.resolve("static/index.html"), "<h1>home</h1>");
    Files.writeString(root.resolve("static/css/app.css"), "body{}");
    Files.writeString(root.resolve("static/docs/index.html"), "docs");
    Files.writeString(root.resolve("static/api"), "shadowed");
    Files.writeString(root.resolve("secret.txt"), "secret");

    classLoader = new URLClassLoader(new URL[] {root.toUri().toURL()}, null);
    handler = new StaticResourceHandler(classLoader, "static",
        new Router().register(new ApiController()));
  }

  @AfterEach
  void tearDown() throws IOException {
    classLoader.close();
  }

  @Test
  void servesIndexForRoot() throws Exception {
    HttpResponse response = handler.handle(request(HttpMethod.GET, "/"));

    assertEquals(HttpStatus.OK, response.status());
    assertEquals(Optional.of("text/html; charset=utf-8"), response.header("Content-Type"));
    assertEquals("<h1>home</h1>", body(response));
  }

  @Test
  void servesNestedFilesAndDirectoryIndexes() throws Exception {
    HttpResponse css = handler.handle(request(HttpMethod.GET, "/css/app.css"));

    assertEquals(Optional.of("text/css; charset=utf-8"), css.header("Content-Type"));
    assertEquals("docs", body(handler.handle(request(HttpMethod.GET, "/docs"))));
    assertEquals("docs", body(handler.handle(request(HttpMethod.HEAD, "/docs/"))));
  }

  @Test
  void prefersRoutesOverFiles() throws Exception {
    assertEquals("from route", body(handler.handle(request(HttpMethod.GET, "/api"))));
  }

  @Test
  void refusesTraversalAndMissingFiles() {
    assertNotFound("/../secret.txt");
    assertNotFound("/css/../../secret.txt");
    assertNotFound("/missing.js");
  }

  @Test
  void ignoresNonReadMethods() {
    HttpException exception = assertThrows(HttpException.class,
        () -> handler.handle(request(HttpMethod.POST, "/index.html")));

    assertEquals(HttpStatus.NOT_FOUND, exception.status());
  }

  private void assertNotFound(String path) {
    HttpException exception = assertThrows(HttpException.class,
        () -> handler.handle(request(HttpMethod.GET, path)));

    assertEquals(HttpStatus.NOT_FOUND, exception.status());
  }

  private static HttpRequest request(HttpMethod method, String path) {
    return new HttpRequest(method, path, "HTTP/1.1", Map.of(), Map.of(), new byte[0]);
  }

  private static String body(HttpResponse response) {
    return new String(response.body(), StandardCharsets.UTF_8);
  }

}
