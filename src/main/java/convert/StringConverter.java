package convert;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public final class StringConverter {

  private static final Map<Class<?>, Function<String, ?>> CONVERTERS = Map.ofEntries(
      Map.entry(String.class, Function.identity()),
      Map.entry(CharSequence.class, Function.identity()),
      Map.entry(int.class, Integer::valueOf),
      Map.entry(Integer.class, Integer::valueOf),
      Map.entry(long.class, Long::valueOf),
      Map.entry(Long.class, Long::valueOf),
      Map.entry(short.class, Short::valueOf),
      Map.entry(Short.class, Short::valueOf),
      Map.entry(byte.class, Byte::valueOf),
      Map.entry(Byte.class, Byte::valueOf),
      Map.entry(double.class, Double::valueOf),
      Map.entry(Double.class, Double::valueOf),
      Map.entry(float.class, Float::valueOf),
      Map.entry(Float.class, Float::valueOf),
      Map.entry(boolean.class, StringConverter::parseBoolean),
      Map.entry(Boolean.class, StringConverter::parseBoolean),
      Map.entry(char.class, StringConverter::parseChar),
      Map.entry(Character.class, StringConverter::parseChar),
      Map.entry(BigDecimal.class, BigDecimal::new),
      Map.entry(BigInteger.class, BigInteger::new),
      Map.entry(UUID.class, UUID::fromString),
      Map.entry(LocalDate.class, LocalDate::parse),
      Map.entry(LocalDateTime.class, LocalDateTime::parse),
      Map.entry(LocalTime.class, LocalTime::parse),
      Map.entry(Instant.class, Instant::parse));

  private StringConverter() {
  }

  public static boolean supports(Class<?> type) {
    return type.isEnum() || CONVERTERS.containsKey(type);
  }

  public static Object convert(String value, Class<?> type) {
    if (!supports(type)) {
      throw new IllegalArgumentException("Unsupported type: " + type.getName());
    }

    try {
      if (type.isEnum()) {
        return parseEnum(value, type);
      }

      return CONVERTERS.get(type).apply(value.trim());
    } catch (RuntimeException e) {
      throw new IllegalArgumentException(
          "Cannot convert '" + value + "' to " + type.getSimpleName(), e);
    }
  }

  private static Boolean parseBoolean(String value) {
    return switch (value.toLowerCase(Locale.ROOT)) {
      case "true" -> true;
      case "false" -> false;
      default -> throw new IllegalArgumentException("Not a boolean: " + value);
    };
  }

  private static Character parseChar(String value) {
    if (value.length() != 1) {
      throw new IllegalArgumentException("Not a single character: " + value);
    }

    return value.charAt(0);
  }

  private static Object parseEnum(String value, Class<?> type) {
    for (Object constant : type.getEnumConstants()) {
      if (((Enum<?>) constant).name().equalsIgnoreCase(value.trim())) {
        return constant;
      }
    }

    throw new IllegalArgumentException("Unknown constant: " + value);
  }

}
