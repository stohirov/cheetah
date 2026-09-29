package core.context;

import http.HttpRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class RequestContext implements AutoCloseable {

  private static final Logger LOGGER = Logger.getLogger(RequestContext.class.getName());
  private static final ThreadLocal<RequestContext> CURRENT = new ThreadLocal<>();

  private final HttpRequest request;
  private final Map<String, Object> attributes = new HashMap<>();
  private final Map<Class<?>, Object> beans = new LinkedHashMap<>();

  private RequestContext(HttpRequest request) {
    this.request = request;
  }

  public static RequestContext open(HttpRequest request) {
    if (CURRENT.get() != null) {
      throw new IllegalStateException("A request context is already active on this thread");
    }

    RequestContext context = new RequestContext(request);
    CURRENT.set(context);

    return context;
  }

  public static Optional<RequestContext> find() {
    return Optional.ofNullable(CURRENT.get());
  }

  public static RequestContext current() {
    return find().orElseThrow(() -> new IllegalStateException("No active request"));
  }

  public HttpRequest request() {
    return request;
  }

  public Optional<Object> attribute(String name) {
    return Optional.ofNullable(attributes.get(name));
  }

  public void setAttribute(String name, Object value) {
    attributes.put(name, value);
  }

  public Map<String, Object> attributes() {
    return Collections.unmodifiableMap(attributes);
  }

  Object bean(Class<?> type) {
    return beans.get(type);
  }

  void putBean(Class<?> type, Object bean) {
    beans.put(type, bean);
  }

  @Override
  public void close() {
    try {
      List<Object> created = new ArrayList<>(beans.values());
      Collections.reverse(created);

      for (Object bean : created) {
        if (bean instanceof AutoCloseable closeable) {
          closeQuietly(closeable);
        }
      }
    } finally {
      beans.clear();
      CURRENT.remove();
    }
  }

  private static void closeQuietly(AutoCloseable closeable) {
    try {
      closeable.close();
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Failed to close request-scoped " + closeable.getClass(), e);
    }
  }

}
