package core.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import annotations.Value;
import config.Config;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApplicationContextTest {

  interface Repository {
    String find();
  }

  static class MemoryRepository implements Repository {

    @Override
    public String find() {
      return "memory";
    }
  }

  static class Service {

    final Repository repository;
    final String greeting;
    final int limit;
    final Config config;

    Service(Repository repository, @Value("greeting:hi") String greeting,
        @Value("limit") int limit, Config config) {
      this.repository = repository;
      this.greeting = greeting;
      this.limit = limit;
      this.config = config;
    }
  }

  interface Plugin {
  }

  static class FirstPlugin implements Plugin {
  }

  static class SecondPlugin implements Plugin {
  }

  static class PluginHost {

    final List<Plugin> plugins;

    PluginHost(List<Plugin> plugins) {
      this.plugins = plugins;
    }
  }

  static class Chicken {

    Chicken(Egg egg) {
    }
  }

  static class Egg {

    Egg(Chicken chicken) {
    }
  }

  static class Exploding {

    Exploding() {
      throw new IllegalStateException("boom");
    }
  }

  private final Config config = new Config("test", Map.of("limit", "5"));

  @Test
  void injectsDependenciesValuesAndConfig() {
    ApplicationContext context = context(MemoryRepository.class, Service.class);

    Service service = context.getBean(Service.class);

    assertSame(service, context.getBean(Service.class));
    assertSame(context.getBean(Repository.class), service.repository);
    assertEquals("hi", service.greeting);
    assertEquals(5, service.limit);
    assertSame(config, service.config);
  }

  @Test
  void injectsAllImplementationsIntoLists() {
    ApplicationContext context = context(FirstPlugin.class, SecondPlugin.class, PluginHost.class);

    PluginHost host = context.getBean(PluginHost.class);

    assertEquals(2, host.plugins.size());
    assertEquals(host.plugins, context.getBeansOfType(Plugin.class));
  }

  @Test
  void detectsCircularDependencies() {
    BeanException exception = assertThrows(BeanException.class,
        () -> context(Chicken.class, Egg.class));

    assertTrue(exception.getMessage().contains("Chicken -> Egg -> Chicken"));
  }

  @Test
  void reportsMissingAndAmbiguousComponents() {
    assertThrows(BeanException.class, () -> context(Service.class));
    assertThrows(BeanException.class,
        () -> context(FirstPlugin.class, SecondPlugin.class).getBean(Plugin.class));
  }

  @Test
  void reportsMissingConfigValue() {
    ApplicationContext context = new ApplicationContext(new Config("test", Map.of()),
        List.of(MemoryRepository.class, Service.class));

    BeanException exception = assertThrows(BeanException.class, context::refresh);

    assertTrue(exception.getMessage().contains("'limit'"));
  }

  @Test
  void wrapsConstructorFailures() {
    BeanException exception = assertThrows(BeanException.class, () -> context(Exploding.class));

    assertEquals("boom", exception.getCause().getMessage());
  }

  private ApplicationContext context(Class<?>... types) {
    ApplicationContext context = new ApplicationContext(config, List.of(types));
    context.refresh();

    return context;
  }

}
