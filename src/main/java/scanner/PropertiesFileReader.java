package scanner;

import java.io.FileInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import models.Server;

public class PropertiesFileReader implements FileReader {

  Map<String, Server> environments = new HashMap<>();

  @Override
  public void readFile(FileInputStream file, List<String> environments) {

  }
}
