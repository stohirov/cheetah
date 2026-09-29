package scanner;

import java.io.IOException;
import java.io.InputStream;

public class ClasspathPropertiesLoader {

  private final ClassLoader classLoader;

  public ClasspathPropertiesLoader(ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  public void load(FileReader fileReader, String environment) throws IOException {
    read(fileReader, "application.properties", FileScanner.DEFAULT_ENVIRONMENT);

    if (!FileScanner.DEFAULT_ENVIRONMENT.equals(environment)) {
      read(fileReader, "application-" + environment + ".properties", environment);
    }
  }

  private void read(FileReader fileReader, String resource, String environment)
      throws IOException {
    try (InputStream input = classLoader.getResourceAsStream(resource)) {
      if (input != null) {
        fileReader.readFile(input, environment);
      }
    }
  }

}
