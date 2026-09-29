package core;

import annotations.Controller;
import config.Config;
import core.routing.Route;
import core.routing.Router;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import scanner.ClassScanner;
import scanner.ClasspathPropertiesLoader;
import scanner.FileScanner;
import scanner.PropertiesFileReader;

public final class ApplicationRunner {

  private static final Logger LOGGER = Logger.getLogger(ApplicationRunner.class.getName());
  private static final String ENV_ARGUMENT = "env";
  private static final String CONFIG_DIR_ARGUMENT = "config-dir";

  private ApplicationRunner() {
  }

  public static CheetahServer run(Class<?> primarySource, String... args) {
    Map<String, String> arguments = parseArguments(args);

    String environment = Optional.ofNullable(arguments.remove(ENV_ARGUMENT))
        .or(() -> Optional.ofNullable(System.getProperty("cheetah.env")))
        .or(() -> Optional.ofNullable(System.getenv("CHEETAH_ENV")))
        .orElse(FileScanner.DEFAULT_ENVIRONMENT);
    String configDirectory = arguments.remove(CONFIG_DIR_ARGUMENT);

    try {
      Config config = loadConfig(primarySource.getClassLoader(), environment, configDirectory)
          .withOverrides(arguments);

      Router router = createRouter(primarySource);

      CheetahServer server = new CheetahServer(config.server(), router);
      server.start();

      Runtime.getRuntime().addShutdownHook(new Thread(() -> stop(server)));
      LOGGER.info("Environment: " + environment);

      return server;
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to start Cheetah", e);
    }
  }

  private static Config loadConfig(ClassLoader classLoader, String environment,
      String configDirectory) throws IOException {
    PropertiesFileReader reader = new PropertiesFileReader();
    new ClasspathPropertiesLoader(classLoader).load(reader, environment);

    if (configDirectory != null) {
      new FileScanner(reader, Path.of(configDirectory)).scanForApplicationProperties();
    }

    return reader.config(environment);
  }

  private static Map<String, String> parseArguments(String[] args) {
    Map<String, String> arguments = new LinkedHashMap<>();

    for (String arg : args) {
      int separator = arg.indexOf('=');
      if (arg.startsWith("--") && separator > 2) {
        arguments.put(arg.substring(2, separator), arg.substring(separator + 1));
      } else {
        LOGGER.warning("Ignoring argument " + arg);
      }
    }

    return arguments;
  }

  private static Router createRouter(Class<?> primarySource) throws IOException {
    ClassScanner scanner = new ClassScanner(primarySource.getClassLoader());
    Router router = new Router();

    for (Class<?> type : scanner.findAnnotated(primarySource.getPackageName(), Controller.class)) {
      router.register(instantiate(type));
    }

    for (Route route : router.routes()) {
      LOGGER.info("Mapped " + route);
    }

    return router;
  }

  private static Object instantiate(Class<?> type) {
    try {
      Constructor<?> constructor = type.getDeclaredConstructor();
      constructor.setAccessible(true);

      return constructor.newInstance();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(
          "Controller " + type.getName() + " needs a no-argument constructor", e);
    }
  }

  private static void stop(CheetahServer server) {
    try {
      server.close();
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "Failed to stop Cheetah", e);
    }
  }

}
