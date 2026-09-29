package scanner;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import models.Server;

public class PropertiesFileReader implements FileReader {

  static final String PORT_KEY = "server.port";
  static final int DEFAULT_PORT = 8080;

  private final Map<String, Properties> environments = new HashMap<>();

  @Override
  public void readFile(InputStream input, String environment) throws IOException {
    Properties properties = new Properties();
    properties.load(input);

    environments.put(environment, properties);
  }

  public Server resolve(String environment) {
    Properties merged = new Properties();
    merged.putAll(environments.getOrDefault(FileScanner.DEFAULT_ENVIRONMENT, new Properties()));
    merged.putAll(environments.getOrDefault(environment, new Properties()));

    return new Server(parsePort(merged.getProperty(PORT_KEY)));
  }

  private static int parsePort(String value) {
    if (value == null || value.isBlank()) {
      return DEFAULT_PORT;
    }

    int port;
    try {
      port = Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid " + PORT_KEY + ": " + value, e);
    }

    if (port < 0 || port > 65535) {
      throw new IllegalArgumentException("Invalid " + PORT_KEY + ": " + value);
    }

    return port;
  }

}
