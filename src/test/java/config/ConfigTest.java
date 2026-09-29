package config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConfigTest {

  @Test
  void readsValuesWithDefaults() {
    Config config = new Config("dev", Map.of("name", "cheetah", "workers", " 4 "));

    assertEquals(Optional.of("cheetah"), config.get("name"));
    assertEquals("fallback", config.get("missing", "fallback"));
    assertEquals(4, config.getInt("workers", 1));
    assertEquals(1, config.getInt("missing", 1));
  }

  @Test
  void appliesOverrides() {
    Config config = new Config("dev", Map.of(Config.PORT_KEY, "8080"))
        .withOverrides(Map.of(Config.PORT_KEY, "9000"));

    assertEquals(9000, config.server().getPort());
    assertEquals("dev", config.environment());
  }

  @Test
  void rejectsInvalidNumbers() {
    Config config = new Config("dev", Map.of(Config.PORT_KEY, "70000", "workers", "many"));

    assertThrows(IllegalArgumentException.class, config::server);
    assertThrows(IllegalArgumentException.class, () -> config.getInt("workers", 1));
  }

}
