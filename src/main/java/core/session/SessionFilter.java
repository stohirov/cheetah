package core.session;

import annotations.Order;
import core.context.RequestContext;
import core.filter.Filter;
import core.filter.FilterChain;
import http.Cookie;
import http.HttpRequest;
import http.HttpResponse;
import java.time.Duration;

@Order(Integer.MIN_VALUE + 2)
public class SessionFilter implements Filter {

  private final SessionStore store;
  private final String cookieName;
  private final boolean secureCookie;

  public SessionFilter(SessionStore store, String cookieName, boolean secureCookie) {
    this.store = store;
    this.cookieName = cookieName;
    this.secureCookie = secureCookie;
  }

  @Override
  public HttpResponse filter(HttpRequest request, FilterChain chain) throws Exception {
    Session original = request.cookie(cookieName).flatMap(store::find).orElse(null);
    SessionHolder holder = new SessionHolder(store, original);

    RequestContext.current().setAttribute(SessionHolder.ATTRIBUTE, holder);

    HttpResponse response = chain.next(request);

    return applySession(response, holder);
  }

  private HttpResponse applySession(HttpResponse response, SessionHolder holder) {
    Session original = holder.original();
    Session current = holder.session();

    if (original != null && original.isInvalidated()) {
      store.remove(original);
    }

    boolean hasActive = current != null && !current.isInvalidated();
    if (hasActive && current != original) {
      return response.withCookie(sessionCookie(current.id()));
    }

    if (current != null && current.isInvalidated()) {
      store.remove(current);
    }

    boolean ended = original != null && !hasActive;
    if (ended) {
      return response.withCookie(sessionCookie("").withMaxAge(Duration.ZERO));
    }

    return response;
  }

  private Cookie sessionCookie(String value) {
    return Cookie.of(cookieName, value)
        .withHttpOnly(true)
        .withSecure(secureCookie)
        .withSameSite(Cookie.SameSite.LAX);
  }

}
