package scanner;

import java.io.FileInputStream;
import java.util.List;

public interface FileReader {

  void readFile(FileInputStream file, List<String> environments);

}
