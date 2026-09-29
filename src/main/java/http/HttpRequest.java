package http;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
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

  public Map<String, String> cookies() {
    Map<String, String> cookies = new LinkedHashMap<>();

    for (String pair : header("cookie").orElse("").split(";")) {
      int separator = pair.indexOf('=');
      if (separator <= 0) {
        continue;
      }

      String name = pair.substring(0, separator).trim();
      String value = pair.substring(separator + 1).trim();
      if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
        value = value.substring(1, value.length() - 1);
      }

      cookies.putIfAbsent(name, value);
    }

    return cookies;
  }

  public Optional<String> cookie(String name) {
    return Optional.ofNullable(cookies().get(name));
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
