package core.session;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class SessionStore {

  private static final int ID_BYTES = 32;
  private static final Duration PURGE_INTERVAL = Duration.ofMinutes(1);

  private final Map<String, Session> sessions = new ConcurrentHashMap<>();
  private final SecureRandom random = new SecureRandom();
  private final Duration timeout;
  private final Clock clock;

  private volatile Instant lastPurge;

  public SessionStore(Duration timeout, Clock clock) {
    this.timeout = timeout;
    this.clock = clock;
    this.lastPurge = clock.instant();
  }

  public Session create() {
    Instant now = clock.instant();
    purgeIfDue(now);

    Session session = new Session(newId(), now);
    sessions.put(session.id(), session);

    return session;
  }

  public Optional<Session> find(String id) {
    Session session = sessions.get(id);
    if (session == null) {
      return Optional.empty();
    }

    Instant now = clock.instant();
    if (session.isInvalidated() || isExpired(session, now)) {
      sessions.remove(id, session);

      return Optional.empty();
    }

    session.touch(now);

    return Optional.of(session);
  }

  public void remove(Session session) {
    sessions.remove(session.id(), session);
  }

  public int size() {
    return sessions.size();
  }

  private boolean isExpired(Session session, Instant now) {
    return session.lastAccessedAt().plus(timeout).isBefore(now);
  }

  private void purgeIfDue(Instant now) {
    if (lastPurge.plus(PURGE_INTERVAL).isAfter(now)) {
      return;
    }

    lastPurge = now;
    sessions.values().removeIf(session -> session.isInvalidated() || isExpired(session, now));
  }

  private String newId() {
    byte[] bytes = new byte[ID_BYTES];
    random.nextBytes(bytes);

    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

}
