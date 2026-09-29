package scanner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class FileScanner {

  public static final String DEFAULT_ENVIRONMENT = "default";

  private static final Path DEFAULT_DIRECTORY = Path.of("src", "main", "resources");
  private static final Pattern PROPERTIES_FILE =
      Pattern.compile("^application(?:-(.+))?\\.properties$");

  private final FileReader fileReader;
  private final Path directory;

  public FileScanner(FileReader fileReader) {
    this(fileReader, DEFAULT_DIRECTORY);
  }

  public FileScanner(FileReader fileReader, Path directory) {
    this.fileReader = fileReader;
    this.directory = directory;
  }

  public void scanForApplicationProperties() throws IOException {
    if (!Files.isDirectory(directory)) {
      return;
    }

    List<Path> files;
    try (Stream<Path> stream = Files.list(directory)) {
      files = stream.filter(Files::isRegularFile).sorted().toList();
    }

    for (Path file : files) {
      Matcher matcher = PROPERTIES_FILE.matcher(file.getFileName().toString());
      if (!matcher.matches()) {
        continue;
      }

      String environment = matcher.group(1) == null ? DEFAULT_ENVIRONMENT : matcher.group(1);

      try (InputStream input = Files.newInputStream(file)) {
        fileReader.readFile(input, environment);
      }
    }
  }

}
