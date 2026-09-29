package http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HttpRequestParserTest {

  private final HttpRequestParser parser = new HttpRequestParser();

  @Test
  void parsesRequestLineQueryAndHeaders() throws IOException {
    HttpRequest request = parseSingle(
        "GET /users/42?sort=name&tag=a&tag=b%20c HTTP/1.1\r\n"
            + "Host: localhost\r\n"
            + "X-Trace: one\r\n"
            + "X-Trace: two\r\n"
            + "\r\n");

    assertEquals(HttpMethod.GET, request.method());
    assertEquals("/users/42", request.path());
    assertEquals(Optional.of("name"), request.queryParam("sort"));
    assertEquals(List.of("a", "b c"), request.queryParams().get("tag"));
    assertEquals(Optional.of("localhost"), request.header("HOST"));
    assertEquals(Optional.of("one, two"), request.header("x-trace"));
    assertEquals(0, request.body().length);
  }

  @Test
  void readsBodyUsingContentLength() throws IOException {
    HttpRequest request = parseSingle(
        "POST /users HTTP/1.1\r\n"
            + "Content-Length: 13\r\n"
            + "\r\n"
            + "{\"name\":\"a\"}\nextra");

    assertEquals("{\"name\":\"a\"}\n", request.bodyAsString());
  }

  @Test
  void parsesConsecutiveRequestsFromSameStream() throws IOException {
    InputStream input = stream(
        "GET /a HTTP/1.1\r\n\r\nGET /b HTTP/1.1\r\nConnection: close\r\n\r\n");

    HttpRequest first = parser.parse(input).orElseThrow();
    HttpRequest second = parser.parse(input).orElseThrow();

    assertEquals("/a", first.path());
    assertTrue(first.keepAlive());
    assertEquals("/b", second.path());
    assertFalse(second.keepAlive());
    assertTrue(parser.parse(input).isEmpty());
  }

  @Test
  void rejectsMalformedRequestLine() {
    HttpException exception = assertThrows(HttpException.class,
        () -> parseSingle("GET /\r\n\r\n"));

    assertEquals(HttpStatus.BAD_REQUEST, exception.status());
  }

  @Test
  void rejectsUnknownMethod() {
    HttpException exception = assertThrows(HttpException.class,
        () -> parseSingle("BREW /pot HTTP/1.1\r\n\r\n"));

    assertEquals(HttpStatus.NOT_IMPLEMENTED, exception.status());
  }

  @Test
  void rejectsChunkedBodies() {
    HttpException exception = assertThrows(HttpException.class,
        () -> parseSingle("POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n"));

    assertEquals(HttpStatus.NOT_IMPLEMENTED, exception.status());
  }

  @Test
  void failsOnTruncatedBody() {
    assertThrows(EOFException.class,
        () -> parseSingle("POST / HTTP/1.1\r\nContent-Length: 10\r\n\r\nabc"));
  }

  private HttpRequest parseSingle(String raw) throws IOException {
    return parser.parse(stream(raw)).orElseThrow();
  }

  private static InputStream stream(String raw) {
    return new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8));
  }

}
