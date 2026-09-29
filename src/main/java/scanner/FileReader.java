package scanner;

import java.io.IOException;
import java.io.InputStream;

public interface FileReader {

  void readFile(InputStream input, String environment) throws IOException;

}
