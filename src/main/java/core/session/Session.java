package core.session;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class Session {

  private final String id;
  private final Instant createdAt;
  private final Map<String, Object> attributes = new ConcurrentHashMap<>();

  private volatile Instant lastAccessedAt;
  private volatile boolean invalidated;

  Session(String id, Instant createdAt) {
    this.id = id;
    this.createdAt = createdAt;
    this.lastAccessedAt = createdAt;
  }

  public String id() {
    return id;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant lastAccessedAt() {
    return lastAccessedAt;
  }

  public Optional<Object> attribute(String name) {
    return Optional.ofNullable(attributes.get(name));
  }

  public <T> Optional<T> attribute(String name, Class<T> type) {
    return attribute(name).filter(type::isInstance).map(type::cast);
  }

  public void setAttribute(String name, Object value) {
    requireValid();

    if (value == null) {
      attributes.remove(name);
    } else {
      attributes.put(name, value);
    }
  }

  public void removeAttribute(String name) {
    attributes.remove(name);
  }

  public Map<String, Object> attributes() {
    return Collections.unmodifiableMap(attributes);
  }

  public void invalidate() {
    invalidated = true;
    attributes.clear();
  }

  public boolean isInvalidated() {
    return invalidated;
  }

  void touch(Instant now) {
    lastAccessedAt = now;
  }

  private void requireValid() {
    if (invalidated) {
      throw new IllegalStateException("Session " + id + " has been invalidated");
    }
  }

}
