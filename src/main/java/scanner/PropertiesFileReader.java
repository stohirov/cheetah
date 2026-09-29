package scanner;

import config.Config;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import models.Server;

public class PropertiesFileReader implements FileReader {

  private final Map<String, Map<String, String>> environments = new HashMap<>();

  @Override
  public void readFile(InputStream input, String environment) throws IOException {
    Properties properties = new Properties();
    properties.load(input);

    Map<String, String> values = environments.computeIfAbsent(environment, key -> new HashMap<>());
    for (String name : properties.stringPropertyNames()) {
      values.put(name, properties.getProperty(name));
    }
  }

  public Config config(String environment) {
    Map<String, String> merged = new HashMap<>();
    merged.putAll(environments.getOrDefault(FileScanner.DEFAULT_ENVIRONMENT, Map.of()));
    merged.putAll(environments.getOrDefault(environment, Map.of()));

    return new Config(environment, merged);
  }

  public Server resolve(String environment) {
    return config(environment).server();
  }

}
