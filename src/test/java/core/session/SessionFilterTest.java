package core.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import core.context.RequestScopeHandler;
import core.filter.FilteringHandler;
import core.handler.Handler;
import http.Cookie;
import http.HttpMethod;
import http.HttpRequest;
import http.HttpResponse;
import http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class SessionFilterTest {

  static class MutableClock extends Clock {

    private Instant now = Instant.parse("2026-09-29T12:00:00Z");

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  private final MutableClock clock = new MutableClock();
  private final SessionStore store = new SessionStore(Duration.ofMinutes(30), clock);

  @Test
  void doesNotCreateSessionsUntilAsked() throws Exception {
    HttpResponse response = send(null, holder -> "none");

    assertEquals(List.of(), response.cookies());
    assertEquals(0, store.size());
  }

  @Test
  void issuesCookieAndRestoresSession() throws Exception {
    HttpResponse first = send(null, holder -> {
      holder.getOrCreate().setAttribute("visits", 1);

      return "created";
    });

    Cookie cookie = first.cookies().get(0);
    assertTrue(cookie.httpOnly());
    assertEquals(Cookie.SameSite.LAX, cookie.sameSite());

    HttpResponse second = send(cookie.value(), holder ->
        String.valueOf(holder.existing().orElseThrow().attribute("visits").orElseThrow()));

    assertEquals("1", body(second));
    assertEquals(List.of(), second.cookies());
  }

  @Test
  void ignoresUnknownAndExpiredSessions() throws Exception {
    String id = createSession();

    assertEquals("absent", body(send("forged", this::describe)));

    clock.advance(Duration.ofMinutes(31));

    assertEquals("absent", body(send(id, this::describe)));
  }

  @Test
  void slidesExpiryOnAccess() throws Exception {
    String id = createSession();

    clock.advance(Duration.ofMinutes(20));
    send(id, this::describe);
    clock.advance(Duration.ofMinutes(20));

    assertEquals("present", body(send(id, this::describe)));
  }

  @Test
  void expiresCookieOnInvalidate() throws Exception {
    String id = createSession();

    HttpResponse response = send(id, holder -> {
      holder.existing().orElseThrow().invalidate();

      return "bye";
    });

    Cookie cookie = response.cookies().get(0);
    assertEquals("", cookie.value());
    assertEquals(Duration.ZERO, cookie.maxAge());
    assertEquals(0, store.size());
  }

  @Test
  void rotatesSessionId() throws Exception {
    String id = createSession();

    HttpResponse response = send(id, holder -> {
      holder.existing().orElseThrow().invalidate();
      holder.getOrCreate().setAttribute("user", "ann");

      return "rotated";
    });

    String rotated = response.cookies().get(0).value();
    assertNotEquals(id, rotated);
    assertEquals(1, store.size());
    assertEquals("absent", body(send(id, this::describe)));
  }

  private String createSession() throws Exception {
    return send(null, holder -> holder.getOrCreate().id()).cookies().get(0).value();
  }

  private String describe(SessionHolder holder) {
    return holder.existing().isPresent() ? "present" : "absent";
  }

  private HttpResponse send(String sessionId, Function<SessionHolder, String> action)
      throws Exception {
    Handler target = request -> HttpResponse.text(HttpStatus.OK,
        action.apply(SessionHolder.current()));
    Handler handler = new RequestScopeHandler(new FilteringHandler(
        List.of(new SessionFilter(store, "SID", false)), target));

    Map<String, String> headers = sessionId == null
        ? Map.of()
        : Map.of("cookie", "SID=" + sessionId);

    return handler.handle(
        new HttpRequest(HttpMethod.GET, "/", "HTTP/1.1", Map.of(), headers, new byte[0]));
  }

  private static String body(HttpResponse response) {
    return new String(response.body(), StandardCharsets.UTF_8);
  }

}
