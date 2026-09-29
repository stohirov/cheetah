package json;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.time.temporal.TemporalAccessor;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class JsonWriter {

  private static final int MAX_DEPTH = 64;

  private final StringBuilder out = new StringBuilder();

  private JsonWriter() {
  }

  static String write(Object value) {
    JsonWriter writer = new JsonWriter();
    writer.writeValue(value, 0);

    return writer.out.toString();
  }

  private void writeValue(Object value, int depth) {
    if (depth > MAX_DEPTH) {
      throw new JsonException("Object graph is too deep or cyclic");
    }

    if (value == null) {
      out.append("null");
    } else if (value instanceof Optional<?> optional) {
      writeValue(optional.orElse(null), depth);
    } else if (value instanceof Boolean) {
      out.append(value);
    } else if (value instanceof Number number) {
      writeNumber(number);
    } else if (isStringLike(value)) {
      writeString(value.toString());
    } else if (value instanceof Enum<?> constant) {
      writeString(constant.name());
    } else if (value instanceof Map<?, ?> map) {
      writeMap(map, depth);
    } else if (value instanceof Iterable<?> iterable) {
      writeArray(iterable.iterator(), depth);
    } else if (value.getClass().isArray()) {
      writeArray(arrayIterator(value), depth);
    } else if (value.getClass().isRecord()) {
      writeMap(recordProperties(value), depth);
    } else {
      writeMap(beanProperties(value), depth);
    }
  }

  private static boolean isStringLike(Object value) {
    return value instanceof CharSequence
        || value instanceof Character
        || value instanceof UUID
        || value instanceof TemporalAccessor
        || value instanceof Duration;
  }

  private void writeNumber(Number number) {
    if (number instanceof Double || number instanceof Float) {
      double asDouble = number.doubleValue();
      if (Double.isNaN(asDouble) || Double.isInfinite(asDouble)) {
        throw new JsonException("Cannot write non-finite number " + number);
      }
    }

    out.append(number);
  }

  private void writeString(String value) {
    out.append('"');

    for (int i = 0; i < value.length(); i++) {
      char current = value.charAt(i);
      switch (current) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        case '\b' -> out.append("\\b");
        case '\f' -> out.append("\\f");
        default -> {
          if (current < 0x20 || current == ' ' || current == ' ') {
            out.append(String.format("\\u%04x", (int) current));
          } else {
            out.append(current);
          }
        }
      }
    }

    out.append('"');
  }

  private void writeMap(Map<?, ?> map, int depth) {
    out.append('{');

    boolean first = true;
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (!first) {
        out.append(',');
      }

      first = false;
      writeString(String.valueOf(entry.getKey()));
      out.append(':');
      writeValue(entry.getValue(), depth + 1);
    }

    out.append('}');
  }

  private void writeArray(Iterator<?> iterator, int depth) {
    out.append('[');

    boolean first = true;
    while (iterator.hasNext()) {
      if (!first) {
        out.append(',');
      }

      first = false;
      writeValue(iterator.next(), depth + 1);
    }

    out.append(']');
  }

  private static Iterator<Object> arrayIterator(Object array) {
    int length = Array.getLength(array);

    return new Iterator<>() {
      private int index;

      @Override
      public boolean hasNext() {
        return index < length;
      }

      @Override
      public Object next() {
        return Array.get(array, index++);
      }
    };
  }

  private static Map<String, Object> recordProperties(Object record) {
    Map<String, Object> properties = new LinkedHashMap<>();

    for (RecordComponent component : record.getClass().getRecordComponents()) {
      Method accessor = component.getAccessor();
      accessor.setAccessible(true);
      properties.put(component.getName(), invoke(accessor, record));
    }

    return properties;
  }

  private static Map<String, Object> beanProperties(Object bean) {
    Map<String, Object> properties = new LinkedHashMap<>();

    for (Field field : bean.getClass().getFields()) {
      if (!Modifier.isStatic(field.getModifiers())) {
        try {
          field.trySetAccessible();
          properties.put(field.getName(), field.get(bean));
        } catch (IllegalAccessException e) {
          throw new JsonException("Cannot read field " + field.getName(), e);
        }
      }
    }

    for (Method method : bean.getClass().getMethods()) {
      String property = propertyName(method);
      if (property != null) {
        properties.putIfAbsent(property, invoke(method, bean));
      }
    }

    return properties;
  }

  private static String propertyName(Method method) {
    if (Modifier.isStatic(method.getModifiers())
        || method.getParameterCount() != 0
        || method.getDeclaringClass() == Object.class) {
      return null;
    }

    String name = method.getName();
    if (name.startsWith("get") && name.length() > 3) {
      return decapitalize(name.substring(3));
    }

    boolean returnsBoolean = method.getReturnType() == boolean.class;
    if (returnsBoolean && name.startsWith("is") && name.length() > 2) {
      return decapitalize(name.substring(2));
    }

    return null;
  }

  private static String decapitalize(String name) {
    return Character.toLowerCase(name.charAt(0)) + name.substring(1);
  }

  private static Object invoke(Method method, Object target) {
    try {
      method.trySetAccessible();

      return method.invoke(target);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new JsonException("Cannot read property " + method.getName(), e);
    }
  }

}
