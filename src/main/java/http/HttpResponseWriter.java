package http;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

public class HttpResponseWriter {

  private static final String CRLF = "\r\n";
  private static final Set<String> MANAGED_HEADERS = Set.of("content-length", "connection");

  public void write(OutputStream output, HttpResponse response, boolean keepAlive)
      throws IOException {
    HttpStatus status = response.status();

    StringBuilder head = new StringBuilder()
        .append("HTTP/1.1 ").append(status.code()).append(' ').append(status.reason())
        .append(CRLF);

    response.headers().forEach((name, value) -> {
      if (!MANAGED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
        head.append(name).append(": ").append(value).append(CRLF);
      }
    });

    if (status != HttpStatus.NO_CONTENT) {
      head.append("Content-Length: ").append(response.body().length).append(CRLF);
    }

    head.append("Connection: ").append(keepAlive ? "keep-alive" : "close").append(CRLF)
        .append(CRLF);

    output.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
    if (status != HttpStatus.NO_CONTENT) {
      output.write(response.body());
    }

    output.flush();
  }

}
