package validation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class Validator {

  private static final java.util.regex.Pattern EMAIL =
      java.util.regex.Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
  private static final Map<Class<?>, List<Property>> PROPERTIES = new ConcurrentHashMap<>();
  private static final Map<String, java.util.regex.Pattern> PATTERNS = new ConcurrentHashMap<>();

  private Validator() {
  }

  public static List<Violation> validate(Object target) {
    List<Violation> violations = new ArrayList<>();
    Set<Object> visiting = Collections.newSetFromMap(new IdentityHashMap<>());

    validateObject("", target, violations, visiting);

    return violations;
  }

  public static void requireValid(Object target) {
    List<Violation> violations = validate(target);

    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
  }

  public static List<Violation> validateValue(String field, Object value,
      Annotation[] annotations) {
    List<Violation> violations = new ArrayList<>();
    check(field, value, annotations, violations);

    boolean cascade = Arrays.stream(annotations).anyMatch(Valid.class::isInstance);
    if (cascade && value != null) {
      cascade(field, value, violations, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    return violations;
  }

  private static void validateObject(String prefix, Object target, List<Violation> violations,
      Set<Object> visiting) {
    if (target == null || isLeaf(target.getClass()) || !visiting.add(target)) {
      return;
    }

    for (Property property : properties(target.getClass())) {
      String field = prefix.isEmpty() ? property.name() : prefix + "." + property.name();
      Object value = property.read(target);

      check(field, value, property.annotations(), violations);

      if (property.cascade() && value != null) {
        cascade(field, value, violations, visiting);
      }
    }
  }

  private static void cascade(String field, Object value, List<Violation> violations,
      Set<Object> visiting) {
    if (value instanceof Map<?, ?> map) {
      map.forEach((key, element) ->
          validateObject(field + "[" + key + "]", element, violations, visiting));
    } else if (value instanceof Iterable<?> iterable) {
      int index = 0;
      for (Object element : iterable) {
        validateObject(field + "[" + index++ + "]", element, violations, visiting);
      }
    } else if (value.getClass().isArray() && !value.getClass().getComponentType().isPrimitive()) {
      for (int i = 0; i < Array.getLength(value); i++) {
        validateObject(field + "[" + i + "]", Array.get(value, i), violations, visiting);
      }
    } else {
      validateObject(field, value, violations, visiting);
    }
  }

  private static void check(String field, Object value, Annotation[] annotations,
      List<Violation> violations) {
    for (Annotation annotation : annotations) {
      String message = violation(value, annotation);
      if (message != null) {
        violations.add(new Violation(field, message));
      }
    }
  }

  private static String violation(Object value, Annotation annotation) {
    if (annotation instanceof NotNull notNull) {
      return value == null ? notNull.message() : null;
    }

    if (annotation instanceof NotBlank notBlank) {
      boolean blank = !(value instanceof CharSequence text) || text.toString().isBlank();

      return blank ? notBlank.message() : null;
    }

    if (annotation instanceof NotEmpty notEmpty) {
      return value == null || length(value, annotation) == 0 ? notEmpty.message() : null;
    }

    if (value == null) {
      return null;
    }

    if (annotation instanceof Size size) {
      int length = length(value, annotation);
      boolean fits = length >= size.min() && length <= size.max();

      return fits ? null : message(size.message(), sizeMessage(size));
    }

    if (annotation instanceof Min min) {
      boolean fits = number(value, annotation).compareTo(BigDecimal.valueOf(min.value())) >= 0;

      return fits ? null
          : message(min.message(), "must be greater than or equal to " + min.value());
    }

    if (annotation instanceof Max max) {
      boolean fits = number(value, annotation).compareTo(BigDecimal.valueOf(max.value())) <= 0;

      return fits ? null : message(max.message(), "must be less than or equal to " + max.value());
    }

    if (annotation instanceof Pattern pattern) {
      java.util.regex.Pattern compiled =
          PATTERNS.computeIfAbsent(pattern.regexp(), java.util.regex.Pattern::compile);
      boolean fits = compiled.matcher(text(value, annotation)).matches();

      return fits ? null : message(pattern.message(), "must match \"" + pattern.regexp() + "\"");
    }

    if (annotation instanceof Email email) {
      return EMAIL.matcher(text(value, annotation)).matches() ? null : email.message();
    }

    return null;
  }

  private static String sizeMessage(Size size) {
    if (size.max() == Integer.MAX_VALUE) {
      return "size must be at least " + size.min();
    }

    return "size must be between " + size.min() + " and " + size.max();
  }

  private static String message(String custom, String fallback) {
    return custom.isEmpty() ? fallback : custom;
  }

  private static int length(Object value, Annotation annotation) {
    if (value instanceof CharSequence text) {
      return text.length();
    }

    if (value instanceof Collection<?> collection) {
      return collection.size();
    }

    if (value instanceof Map<?, ?> map) {
      return map.size();
    }

    if (value.getClass().isArray()) {
      return Array.getLength(value);
    }

    throw unsupported(value, annotation);
  }

  private static BigDecimal number(Object value, Annotation annotation) {
    if (value instanceof Number number) {
      return new BigDecimal(number.toString());
    }

    throw unsupported(value, annotation);
  }

  private static CharSequence text(Object value, Annotation annotation) {
    if (value instanceof CharSequence text) {
      return text;
    }

    throw unsupported(value, annotation);
  }

  private static IllegalArgumentException unsupported(Object value, Annotation annotation) {
    return new IllegalArgumentException("@" + annotation.annotationType().getSimpleName()
        + " is not supported on " + value.getClass().getSimpleName());
  }

  private static boolean isLeaf(Class<?> type) {
    return type.isPrimitive()
        || type.isEnum()
        || type.getName().startsWith("java.")
        || type.isArray();
  }

  private static List<Property> properties(Class<?> type) {
    return PROPERTIES.computeIfAbsent(type, Validator::discoverProperties);
  }

  private static List<Property> discoverProperties(Class<?> type) {
    List<Property> properties = new ArrayList<>();

    if (type.isRecord()) {
      for (RecordComponent component : type.getRecordComponents()) {
        Method accessor = component.getAccessor();
        accessor.trySetAccessible();
        properties.add(new Property(component.getName(), component.getAnnotations(),
            target -> invoke(accessor, target)));
      }

      return properties;
    }

    for (Class<?> current = type; current != Object.class; current = current.getSuperclass()) {
      for (Field field : current.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
          continue;
        }

        field.trySetAccessible();
        properties.add(new Property(field.getName(), field.getAnnotations(),
            target -> read(field, target)));
      }
    }

    return properties;
  }

  private static Object invoke(Method accessor, Object target) {
    try {
      return accessor.invoke(target);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Cannot read " + accessor.getName(), e);
    }
  }

  private static Object read(Field field, Object target) {
    try {
      return field.get(target);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("Cannot read " + field.getName(), e);
    }
  }

  private interface Reader {
    Object read(Object target);
  }

  private record Property(String name, Annotation[] annotations, Reader reader) {

    Object read(Object target) {
      return reader.read(target);
    }

    boolean cascade() {
      return Arrays.stream(annotations).anyMatch(Valid.class::isInstance);
    }
  }

}
