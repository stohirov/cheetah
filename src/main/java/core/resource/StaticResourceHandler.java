package core.resource;

import core.handler.Handler;
import core.routing.Router;
import http.HttpMethod;
import http.HttpRequest;
import http.HttpResponse;
import http.HttpStatus;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class StaticResourceHandler implements Handler {

  private static final String INDEX = "index.html";
  private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
  private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
      Map.entry("html", "text/html; charset=utf-8"),
      Map.entry("css", "text/css; charset=utf-8"),
      Map.entry("js", "text/javascript; charset=utf-8"),
      Map.entry("json", "application/json; charset=utf-8"),
      Map.entry("txt", "text/plain; charset=utf-8"),
      Map.entry("svg", "image/svg+xml"),
      Map.entry("png", "image/png"),
      Map.entry("jpg", "image/jpeg"),
      Map.entry("jpeg", "image/jpeg"),
      Map.entry("gif", "image/gif"),
      Map.entry("webp", "image/webp"),
      Map.entry("ico", "image/x-icon"),
      Map.entry("woff2", "font/woff2"));

  private final ClassLoader classLoader;
  private final String root;
  private final Router router;

  public StaticResourceHandler(ClassLoader classLoader, String root, Router router) {
    this.classLoader = classLoader;
    this.root = root;
    this.router = router;
  }

  @Override
  public HttpResponse handle(HttpRequest request) throws Exception {
    boolean readable = request.method() == HttpMethod.GET || request.method() == HttpMethod.HEAD;

    if (readable && !router.hasRoute(request.path())) {
      Optional<HttpResponse> resource = load(request.path());
      if (resource.isPresent()) {
        return resource.get();
      }
    }

    return router.handle(request);
  }

  private Optional<HttpResponse> load(String path) throws IOException {
    List<String> segments = Arrays.stream(path.split("/"))
        .filter(segment -> !segment.isEmpty())
        .toList();

    boolean unsafe = segments.stream().anyMatch(segment ->
        segment.equals("..") || segment.equals(".") || segment.contains("\\"));
    if (unsafe) {
      return Optional.empty();
    }

    String name = resourceName(segments, path.endsWith("/"));

    Optional<URL> resource = findFile(name).or(() -> findFile(name + "/" + INDEX));
    if (resource.isEmpty()) {
      return Optional.empty();
    }

    byte[] content;
    try (InputStream input = resource.get().openStream()) {
      content = input.readAllBytes();
    }

    String fileName = resource.get().getPath();

    return Optional.of(new HttpResponse(HttpStatus.OK,
        Map.of("Content-Type", contentType(fileName)), content));
  }

  private String resourceName(List<String> segments, boolean directory) {
    if (segments.isEmpty()) {
      return root + "/" + INDEX;
    }

    String name = root + "/" + String.join("/", segments);

    return directory ? name + "/" + INDEX : name;
  }

  private Optional<URL> findFile(String name) {
    URL url = classLoader.getResource(name);
    if (url == null) {
      return Optional.empty();
    }

    if (!"file".equals(url.getProtocol())) {
      return name.endsWith("/") ? Optional.empty() : Optional.of(url);
    }

    try {
      return Files.isRegularFile(Path.of(url.toURI())) ? Optional.of(url) : Optional.empty();
    } catch (URISyntaxException e) {
      return Optional.empty();
    }
  }

  private static String contentType(String fileName) {
    int dot = fileName.lastIndexOf('.');
    if (dot < 0) {
      return DEFAULT_CONTENT_TYPE;
    }

    String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);

    return CONTENT_TYPES.getOrDefault(extension, DEFAULT_CONTENT_TYPE);
  }

}
