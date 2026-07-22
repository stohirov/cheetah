package scanner;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class FileScanner {

  private final FileReader fileReader;

  public FileScanner(FileReader fileReader) {
    this.fileReader = new PropertiesFileReader();
  }

  public void scanForApplicationProperties() {
    Path paths = Paths.get("src/main/resources");
    try (Stream<Path> stream = Files.list(paths)) {
      stream
          .filter(path -> Files.isRegularFile(path)
              && path.getFileName().toString().endsWith(".properties")
              && path.getFileName().toString().startsWith("application")
          )

          .forEach(path -> {
            String fileName = path.getFileName().toString();
            String[] nameParts = fileName.split("-");

            List<String> environments = Arrays.asList(Arrays.copyOfRange(nameParts,
                1, nameParts.length));

            try (FileInputStream input = new FileInputStream(fileName)) {
              fileReader.readFile(input, environments);
            } catch (IOException e) {
              e.printStackTrace();
            }

          });
    } catch (Exception e) {
      System.out.println("Could find any application property files");
      e.printStackTrace();
    }
  }

}
