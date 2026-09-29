package core;

import annotations.Controller;
import core.routing.Route;
import core.routing.Router;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import models.Server;
import scanner.ClassScanner;
import scanner.FileScanner;
import scanner.PropertiesFileReader;

public final class ApplicationRunner {

  private static final Logger LOGGER = Logger.getLogger(ApplicationRunner.class.getName());
  private static final String ENV_ARGUMENT = "--env=";
  private static final String PORT_ARGUMENT = "--port=";

  private ApplicationRunner() {
  }

  public static CheetahServer run(Class<?> primarySource, String... args) {
    String environment = argument(args, ENV_ARGUMENT)
        .or(() -> Optional.ofNullable(System.getProperty("cheetah.env")))
        .or(() -> Optional.ofNullable(System.getenv("CHEETAH_ENV")))
        .orElse(FileScanner.DEFAULT_ENVIRONMENT);

    try {
      Server config = argument(args, PORT_ARGUMENT)
          .map(port -> new Server(Integer.parseInt(port)))
          .orElse(loadConfig(environment));

      Router router = createRouter(primarySource);

      CheetahServer server = new CheetahServer(config, router);
      server.start();

      Runtime.getRuntime().addShutdownHook(new Thread(() -> stop(server)));
      LOGGER.info("Environment: " + environment);

      return server;
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to start Cheetah", e);
    }
  }

  private static Server loadConfig(String environment) throws IOException {
    PropertiesFileReader reader = new PropertiesFileReader();
    new FileScanner(reader).scanForApplicationProperties();

    return reader.resolve(environment);
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

  private static Optional<String> argument(String[] args, String prefix) {
    return Arrays.stream(args)
        .filter(arg -> arg.startsWith(prefix))
        .map(arg -> arg.substring(prefix.length()))
        .reduce((first, second) -> second);
  }

  private static void stop(CheetahServer server) {
    try {
      server.close();
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "Failed to stop Cheetah", e);
    }
  }

}
