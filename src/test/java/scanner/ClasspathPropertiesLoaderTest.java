package scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;

import config.Config;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClasspathPropertiesLoaderTest {

  @TempDir
  Path root;

  @Test
  void mergesBaseAndEnvironmentResources() throws IOException {
    Files.writeString(root.resolve("application.properties"), "server.port=8000\nname=base");
    Files.writeString(root.resolve("application-prod.properties"), "server.port=9000");

    Config config = load("prod");

    assertEquals(9000, config.server().getPort());
    assertEquals(Optional.of("base"), config.get("name"));
  }

  @Test
  void usesDefaultsWhenResourcesAreMissing() throws IOException {
    assertEquals(Config.DEFAULT_PORT, load("prod").server().getPort());
  }

  private Config load(String environment) throws IOException {
    PropertiesFileReader reader = new PropertiesFileReader();

    try (URLClassLoader classLoader = new URLClassLoader(new URL[] {root.toUri().toURL()}, null)) {
      new ClasspathPropertiesLoader(classLoader).load(reader, environment);
    }

    return reader.config(environment);
  }

}
