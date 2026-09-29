package http;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public record HttpRequest(
    HttpMethod method,
    String path,
    String version,
    Map<String, List<String>> queryParams,
    Map<String, String> headers,
    byte[] body) {

  public HttpRequest {
    queryParams = Map.copyOf(queryParams);
    headers = Map.copyOf(headers);
  }

  public Optional<String> header(String name) {
    return Optional.ofNullable(headers.get(name.toLowerCase(Locale.ROOT)));
  }

  public Optional<String> queryParam(String name) {
    List<String> values = queryParams.getOrDefault(name, List.of());

    return values.stream().findFirst();
  }

  public String bodyAsString() {
    return new String(body, StandardCharsets.UTF_8);
  }

  public boolean keepAlive() {
    String connection = header("connection").orElse("").toLowerCase(Locale.ROOT);

    if ("HTTP/1.0".equals(version)) {
      return connection.contains("keep-alive");
    }

    return !connection.contains("close");
  }
}
