package http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class HttpResponseWriterTest {

  private final HttpResponseWriter writer = new HttpResponseWriter();

  @Test
  void writesStatusHeadersAndBody() throws IOException {
    HttpResponse response = HttpResponse.text(HttpStatus.OK, "hi").withHeader("X-Id", "7");

    assertEquals(
        "HTTP/1.1 200 OK\r\n"
            + "Content-Type: text/plain; charset=utf-8\r\n"
            + "X-Id: 7\r\n"
            + "Content-Length: 2\r\n"
            + "Connection: keep-alive\r\n"
            + "\r\n"
            + "hi",
        write(response, true));
  }

  @Test
  void omitsBodyForNoContent() throws IOException {
    assertEquals(
        "HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n",
        write(HttpResponse.of(HttpStatus.NO_CONTENT), false));
  }

  @Test
  void omitsBodyButKeepsLengthWhenRequested() throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    writer.write(output, HttpResponse.text(HttpStatus.OK, "hello"), true, false);

    String raw = output.toString(StandardCharsets.UTF_8);

    assertTrue(raw.contains("Content-Length: 5\r\n"));
    assertTrue(raw.endsWith("\r\n\r\n"));
  }

  @Test
  void rejectsHeaderInjection() {
    assertThrows(IllegalArgumentException.class,
        () -> HttpResponse.of(HttpStatus.OK).withHeader("X-Bad", "a\r\nSet-Cookie: x"));
  }

  private String write(HttpResponse response, boolean keepAlive) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    writer.write(output, response, keepAlive);

    return output.toString(StandardCharsets.UTF_8);
  }

}
