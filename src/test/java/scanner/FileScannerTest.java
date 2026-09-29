package scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import config.Config;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileScannerTest {

  @TempDir
  Path directory;

  @Test
  void resolvesEnvironmentOverridingDefault() throws IOException {
    Files.writeString(directory.resolve("application.properties"), "server.port=8081");
    Files.writeString(directory.resolve("application-dev.properties"), "server.port=9090");
    Files.writeString(directory.resolve("other.properties"), "server.port=1");

    PropertiesFileReader reader = scan();

    assertEquals(9090, reader.resolve("dev").getPort());
    assertEquals(8081, reader.resolve("default").getPort());
    assertEquals(8081, reader.resolve("prod").getPort());
  }

  @Test
  void fallsBackToDefaultPortWhenNothingConfigured() throws IOException {
    PropertiesFileReader reader = scan();

    assertEquals(Config.DEFAULT_PORT, reader.resolve("default").getPort());
  }

  @Test
  void ignoresMissingDirectory() throws IOException {
    PropertiesFileReader reader = new PropertiesFileReader();

    new FileScanner(reader, directory.resolve("missing")).scanForApplicationProperties();

    assertEquals(Config.DEFAULT_PORT, reader.resolve("default").getPort());
  }

  @Test
  void rejectsInvalidPort() throws IOException {
    Files.writeString(directory.resolve("application.properties"), "server.port=abc");

    PropertiesFileReader reader = scan();

    assertThrows(IllegalArgumentException.class, () -> reader.resolve("default"));
  }

  private PropertiesFileReader scan() throws IOException {
    PropertiesFileReader reader = new PropertiesFileReader();
    new FileScanner(reader, directory).scanForApplicationProperties();

    return reader;
  }

}
