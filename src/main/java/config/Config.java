package config;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import models.Server;
import models.TlsSettings;

public final class Config {

  public static final String PORT_KEY = "server.port";
  public static final int DEFAULT_PORT = 8080;
  public static final String SSL_ENABLED_KEY = "server.ssl.enabled";

  private final String environment;
  private final Map<String, String> properties;

  public Config(String environment, Map<String, String> properties) {
    this.environment = environment;
    this.properties = Map.copyOf(properties);
  }

  public String environment() {
    return environment;
  }

  public Optional<String> get(String key) {
    return Optional.ofNullable(properties.get(key));
  }

  public String get(String key, String defaultValue) {
    return get(key).orElse(defaultValue);
  }

  public int getInt(String key, int defaultValue) {
    Optional<String> value = get(key).filter(raw -> !raw.isBlank());
    if (value.isEmpty()) {
      return defaultValue;
    }

    try {
      return Integer.parseInt(value.get().trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid " + key + ": " + value.get(), e);
    }
  }

  public Server server() {
    int port = getInt(PORT_KEY, DEFAULT_PORT);

    if (port < 0 || port > 65535) {
      throw new IllegalArgumentException("Invalid " + PORT_KEY + ": " + port);
    }

    if (!Boolean.parseBoolean(get(SSL_ENABLED_KEY, "false"))) {
      return new Server(port);
    }

    TlsSettings tls = new TlsSettings(
        get("server.ssl.key-store").orElse(null),
        get("server.ssl.key-store-password").orElse(null),
        get("server.ssl.key-store-type", "PKCS12"),
        get("server.ssl.key-password").orElse(null));

    return new Server(port, tls);
  }

  public Config withOverrides(Map<String, String> overrides) {
    Map<String, String> merged = new HashMap<>(properties);
    merged.putAll(overrides);

    return new Config(environment, merged);
  }

}
