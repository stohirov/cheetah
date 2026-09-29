package core.session;

import core.context.RequestContext;
import java.util.Optional;

public final class SessionHolder {

  static final String ATTRIBUTE = SessionHolder.class.getName();

  private final SessionStore store;
  private final Session original;

  private Session current;

  SessionHolder(SessionStore store, Session original) {
    this.store = store;
    this.original = original;
    this.current = original;
  }

  public static SessionHolder current() {
    return RequestContext.current()
        .attribute(ATTRIBUTE)
        .map(SessionHolder.class::cast)
        .orElseThrow(() -> new IllegalStateException("Sessions are disabled"));
  }

  public Optional<Session> existing() {
    return Optional.ofNullable(current).filter(session -> !session.isInvalidated());
  }

  public Session getOrCreate() {
    if (current == null || current.isInvalidated()) {
      current = store.create();
    }

    return current;
  }

  Session original() {
    return original;
  }

  Session session() {
    return current;
  }

}
