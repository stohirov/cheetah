package http;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class HttpException extends RuntimeException {

  private final HttpStatus status;
  private final Map<String, String> headers = new LinkedHashMap<>();

  public HttpException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public HttpException(HttpStatus status, String message, Throwable cause) {
    super(message, cause);
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }

  public HttpException withHeader(String name, String value) {
    headers.put(name, value);

    return this;
  }

  public Map<String, String> headers() {
    return Collections.unmodifiableMap(headers);
  }
}
