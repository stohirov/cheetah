package scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import annotations.Controller;
import java.io.IOException;
import java.util.List;
import org.apiguardian.api.API;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ClassScannerTest {

  private final ClassScanner scanner = new ClassScanner(getClass().getClassLoader());

  @Test
  void findsAnnotatedClassesInSubpackages() throws IOException {
    List<String> names = scanner.findAnnotated("sampleapp", Controller.class).stream()
        .map(Class::getName)
        .toList();

    assertEquals(List.of("sampleapp.api.GreetingController"), names);
  }

  @Test
  void findsAnnotatedClassesInsideJars() throws IOException {
    List<Class<?>> types = scanner.findAnnotated("org.junit.jupiter.api", API.class);

    assertTrue(types.contains(Assertions.class));
  }

}
