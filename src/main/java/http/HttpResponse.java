package http;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public record HttpResponse(HttpStatus status, Map<String, String> headers, byte[] body) {

  public static final String TEXT_PLAIN = "text/plain; charset=utf-8";
  public static final String APPLICATION_JSON = "application/json; charset=utf-8";

  public HttpResponse {
    Map<String, String> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    headers.forEach((name, value) -> {
      if (containsLineBreak(name) || containsLineBreak(value)) {
        throw new IllegalArgumentException("Header must not contain line breaks: " + name);
      }

      copy.put(name, value);
    });

    headers = Collections.unmodifiableMap(copy);
  }

  public static HttpResponse of(HttpStatus status) {
    return new HttpResponse(status, Map.of(), new byte[0]);
  }

  public static HttpResponse text(HttpStatus status, String text) {
    return new HttpResponse(status, Map.of("Content-Type", TEXT_PLAIN),
        text.getBytes(StandardCharsets.UTF_8));
  }

  public static HttpResponse json(HttpStatus status, String json) {
    return new HttpResponse(status, Map.of("Content-Type", APPLICATION_JSON),
        json.getBytes(StandardCharsets.UTF_8));
  }

  public HttpResponse withHeader(String name, String value) {
    Map<String, String> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    copy.putAll(headers);
    copy.put(name, value);

    return new HttpResponse(status, copy, body);
  }

  public HttpResponse withBody(byte[] newBody) {
    return new HttpResponse(status, headers, newBody);
  }

  public Optional<String> header(String name) {
    return Optional.ofNullable(headers.get(name));
  }

  private static boolean containsLineBreak(String value) {
    return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
  }
}
