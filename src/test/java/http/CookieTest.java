package http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CookieTest {

  @Test
  void formatsAllAttributes() {
    Cookie cookie = Cookie.of("session", "abc123")
        .withDomain("example.com")
        .withMaxAge(Duration.ofHours(1))
        .withSecure(true)
        .withHttpOnly(true)
        .withSameSite(Cookie.SameSite.STRICT);

    assertEquals("session=abc123; Path=/; Domain=example.com; Max-Age=3600; Secure; HttpOnly;"
        + " SameSite=Strict", cookie.toHeader());
  }

  @Test
  void expiresCookies() {
    assertEquals("theme=; Path=/; Max-Age=0", Cookie.expired("theme").toHeader());
  }

  @Test
  void rejectsUnsafeNamesAndValues() {
    assertThrows(IllegalArgumentException.class, () -> Cookie.of("bad name", "x"));
    assertThrows(IllegalArgumentException.class, () -> Cookie.of("name", "a;b"));
    assertThrows(IllegalArgumentException.class, () -> Cookie.of("name", "a\r\nb"));
    assertThrows(IllegalArgumentException.class, () -> Cookie.of("name", "x").withPath("/;x"));
  }

  @Test
  void parsesRequestCookies() {
    HttpRequest request = new HttpRequest(HttpMethod.GET, "/", "HTTP/1.1", Map.of(),
        Map.of("cookie", "theme=dark; session=\"abc\"; theme=light; broken"), new byte[0]);

    assertEquals(Map.of("theme", "dark", "session", "abc"), request.cookies());
    assertEquals(Optional.of("abc"), request.cookie("session"));
    assertEquals(Optional.empty(), request.cookie("missing"));
  }

  @Test
  void writesOneHeaderPerCookie() throws IOException {
    HttpResponse response = HttpResponse.of(HttpStatus.OK)
        .withCookie(Cookie.of("a", "1"))
        .withHeader("X-Test", "yes")
        .withCookie(Cookie.of("b", "2"));

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    new HttpResponseWriter().write(output, response, false);
    String raw = output.toString(StandardCharsets.UTF_8);

    assertTrue(raw.contains("Set-Cookie: a=1; Path=/\r\nSet-Cookie: b=2; Path=/\r\n"));
  }

}
