package scanner;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class ClassScanner {

  private static final Logger LOGGER = Logger.getLogger(ClassScanner.class.getName());
  private static final String CLASS_SUFFIX = ".class";

  private final ClassLoader classLoader;

  public ClassScanner(ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  public List<Class<?>> findAnnotated(String packageName, Class<? extends Annotation> annotation)
      throws IOException {
    List<Class<?>> matches = new ArrayList<>();

    for (String className : findClassNames(packageName)) {
      Optional<Class<?>> loaded = load(className);
      if (loaded.isEmpty()) {
        continue;
      }

      Class<?> type = loaded.get();
      boolean concrete = !type.isInterface() && !Modifier.isAbstract(type.getModifiers());
      if (concrete && type.isAnnotationPresent(annotation)) {
        matches.add(type);
      }
    }

    matches.sort(Comparator.comparing(Class::getName));

    return matches;
  }

  private Set<String> findClassNames(String packageName) throws IOException {
    String packagePath = packageName.replace('.', '/');
    Set<String> classNames = new TreeSet<>();

    Enumeration<URL> resources = classLoader.getResources(packagePath);
    while (resources.hasMoreElements()) {
      URL resource = resources.nextElement();

      switch (resource.getProtocol()) {
        case "file" -> classNames.addAll(scanDirectory(resource, packageName));
        case "jar" -> classNames.addAll(scanJar(resource, packagePath));
        default -> {
        }
      }
    }

    return classNames;
  }

  private static List<String> scanDirectory(URL resource, String packageName) throws IOException {
    Path root;
    try {
      root = Path.of(resource.toURI());
    } catch (URISyntaxException e) {
      throw new IOException("Invalid class path entry: " + resource, e);
    }

    String prefix = packageName.isEmpty() ? "" : packageName + ".";

    try (Stream<Path> files = Files.walk(root)) {
      return files
          .filter(file -> file.getFileName().toString().endsWith(CLASS_SUFFIX))
          .map(file -> prefix + toClassName(root.relativize(file).toString(), file))
          .toList();
    }
  }

  private static String toClassName(String relativePath, Path file) {
    String separator = file.getFileSystem().getSeparator();
    String withoutSuffix =
        relativePath.substring(0, relativePath.length() - CLASS_SUFFIX.length());

    return withoutSuffix.replace(separator, ".");
  }

  private static List<String> scanJar(URL resource, String packagePath) throws IOException {
    JarURLConnection connection = (JarURLConnection) resource.openConnection();
    connection.setUseCaches(false);

    List<String> classNames = new ArrayList<>();
    try (JarFile jar = connection.getJarFile()) {
      Enumeration<JarEntry> entries = jar.entries();

      while (entries.hasMoreElements()) {
        String name = entries.nextElement().getName();
        if (name.startsWith(packagePath + "/") && name.endsWith(CLASS_SUFFIX)) {
          classNames.add(name.substring(0, name.length() - CLASS_SUFFIX.length())
              .replace('/', '.'));
        }
      }
    }

    return classNames;
  }

  private Optional<Class<?>> load(String className) {
    try {
      return Optional.of(Class.forName(className, false, classLoader));
    } catch (ClassNotFoundException | LinkageError e) {
      LOGGER.log(Level.FINE, "Skipping " + className, e);

      return Optional.empty();
    }
  }

}
