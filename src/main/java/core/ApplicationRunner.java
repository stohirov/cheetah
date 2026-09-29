package core;

import annotations.Component;
import annotations.Controller;
import config.Config;
import core.context.ApplicationContext;
import core.context.RequestScopeHandler;
import core.error.ErrorHandlingHandler;
import core.error.ExceptionHandlers;
import core.filter.AccessLogFilter;
import core.filter.CompressionFilter;
import core.filter.Filter;
import core.filter.FilteringHandler;
import core.handler.Handler;
import core.resource.StaticResourceHandler;
import core.session.SessionFilter;
import core.session.SessionStore;
import core.routing.Route;
import core.routing.Router;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
  private static final String ACCESS_LOG_KEY = "server.access-log";
  private static final String STATIC_ROOT_KEY = "server.static-root";
  private static final String SESSION_KEY = "server.session.enabled";
  private static final String SESSION_TIMEOUT_KEY = "server.session.timeout-seconds";
  private static final String SESSION_COOKIE_KEY = "server.session.cookie-name";
  private static final String SESSION_SECURE_KEY = "server.session.cookie-secure";
  private static final String COMPRESSION_KEY = "server.compression.enabled";
  private static final String COMPRESSION_MIN_SIZE_KEY = "server.compression.min-size";

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

      ApplicationContext context = createContext(primarySource, config);
      Router router = createRouter(context);
      ExceptionHandlers exceptionHandlers = createExceptionHandlers(context);

      Handler resources = new StaticResourceHandler(primarySource.getClassLoader(),
          config.get(STATIC_ROOT_KEY, "static"), router);
      Handler routes = new ErrorHandlingHandler(resources, exceptionHandlers);
      Handler filtered = new FilteringHandler(createFilters(context, config), routes);
      Handler handler = new RequestScopeHandler(
          new ErrorHandlingHandler(filtered, exceptionHandlers));

      CheetahServer server = new CheetahServer(config.server(), handler);
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

  private static ApplicationContext createContext(Class<?> primarySource, Config config)
      throws IOException {
    ClassScanner scanner = new ClassScanner(primarySource.getClassLoader());
    String packageName = primarySource.getPackageName();

    Set<Class<?>> componentTypes = new LinkedHashSet<>();
    componentTypes.addAll(scanner.findAnnotated(packageName, Component.class));
    componentTypes.addAll(scanner.findAnnotated(packageName, Controller.class));

    ApplicationContext context = new ApplicationContext(config, componentTypes);
    context.refresh();

    return context;
  }

  private static Router createRouter(ApplicationContext context) {
    Router router = new Router();

    for (Class<?> type : context.typesWithAnnotation(Controller.class)) {
      router.register(type, context.beanSupplier(type));
    }

    for (Route route : router.routes()) {
      LOGGER.info("Mapped " + route);
    }

    return router;
  }

  private static List<Filter> createFilters(ApplicationContext context, Config config) {
    List<Filter> filters = new ArrayList<>(context.getBeansOfType(Filter.class));

    if (Boolean.parseBoolean(config.get(ACCESS_LOG_KEY, "false"))) {
      filters.add(new AccessLogFilter());
    }

    if (Boolean.parseBoolean(config.get(SESSION_KEY, "true"))) {
      SessionStore store = new SessionStore(
          Duration.ofSeconds(config.getInt(SESSION_TIMEOUT_KEY, 1800)), Clock.systemUTC());

      filters.add(new SessionFilter(store, config.get(SESSION_COOKIE_KEY, "CHEETAH_SESSION"),
          Boolean.parseBoolean(config.get(SESSION_SECURE_KEY, "false"))));
    }

    if (Boolean.parseBoolean(config.get(COMPRESSION_KEY, "true"))) {
      filters.add(new CompressionFilter(config.getInt(COMPRESSION_MIN_SIZE_KEY, 1024)));
    }

    return filters;
  }

  private static ExceptionHandlers createExceptionHandlers(ApplicationContext context) {
    ExceptionHandlers exceptionHandlers = new ExceptionHandlers();

    for (Class<?> type : context.componentTypes()) {
      exceptionHandlers.register(type, context.beanSupplier(type));
    }

    return exceptionHandlers;
  }

  private static void stop(CheetahServer server) {
    try {
      server.close();
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "Failed to stop Cheetah", e);
    }
  }

}
