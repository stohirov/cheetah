package convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StringConverterTest {

  enum Color {
    RED
  }

  @Test
  void convertsSupportedTypes() {
    UUID id = UUID.randomUUID();

    assertEquals(42, StringConverter.convert("42", int.class));
    assertEquals(42L, StringConverter.convert(" 42 ", Long.class));
    assertEquals(true, StringConverter.convert("TRUE", boolean.class));
    assertEquals(Color.RED, StringConverter.convert("red", Color.class));
    assertEquals(id, StringConverter.convert(id.toString(), UUID.class));
    assertEquals(LocalDate.of(2026, 9, 29), StringConverter.convert("2026-09-29", LocalDate.class));
  }

  @Test
  void rejectsInvalidValues() {
    assertThrows(IllegalArgumentException.class,
        () -> StringConverter.convert("x", int.class));
    assertThrows(IllegalArgumentException.class,
        () -> StringConverter.convert("yes", boolean.class));
    assertThrows(IllegalArgumentException.class,
        () -> StringConverter.convert("BLUE", Color.class));
  }

  @Test
  void reportsUnsupportedTypes() {
    assertFalse(StringConverter.supports(Object.class));
    assertThrows(IllegalArgumentException.class, () -> StringConverter.convert("x", Object.class));
  }

}
