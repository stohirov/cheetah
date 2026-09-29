package json;

import convert.StringConverter;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class JsonMapper {

  private static final Map<Class<?>, Object> PRIMITIVE_DEFAULTS = Map.of(
      boolean.class, false,
      char.class, '\0',
      byte.class, (byte) 0,
      short.class, (short) 0,
      int.class, 0,
      long.class, 0L,
      float.class, 0f,
      double.class, 0d);

  private JsonMapper() {
  }

  static Object convert(Object node, Type type) {
    Class<?> raw = rawClass(type);

    if (node == null) {
      if (raw.isPrimitive()) {
        throw new JsonException("Cannot assign null to " + raw.getName());
      }

      return null;
    }

    if (raw == Object.class) {
      return node;
    }

    if (node instanceof String text && StringConverter.supports(raw)) {
      return convertString(text, raw);
    }

    if (node instanceof Number number && isNumeric(raw)) {
      return convertNumber(number, raw);
    }

    if (node instanceof Boolean && (raw == boolean.class || raw == Boolean.class)) {
      return node;
    }

    if (node instanceof List<?> list) {
      if (raw.isArray()) {
        return toArray(list, componentType(type));
      }

      if (Collection.class.isAssignableFrom(raw)) {
        return toCollection(list, raw, typeArgument(type, 0));
      }
    }

    if (node instanceof Map<?, ?> map) {
      if (Map.class.isAssignableFrom(raw)) {
        return toMap(map, typeArgument(type, 0), typeArgument(type, 1));
      }

      if (raw.isRecord()) {
        return toRecord(map, raw);
      }

      if (!raw.isInterface() && !Modifier.isAbstract(raw.getModifiers())) {
        return toBean(map, raw);
      }
    }

    throw new JsonException("Cannot convert " + describe(node) + " to " + type.getTypeName());
  }

  private static Object convertString(String text, Class<?> raw) {
    try {
      return StringConverter.convert(text, raw);
    } catch (IllegalArgumentException e) {
      throw new JsonException(e.getMessage(), e);
    }
  }

  private static boolean isNumeric(Class<?> raw) {
    return raw == BigDecimal.class
        || raw == BigInteger.class
        || (raw.isPrimitive() && raw != boolean.class && raw != char.class)
        || (Number.class.isAssignableFrom(raw) && raw.getPackageName().equals("java.lang"));
  }

  private static Object convertNumber(Number number, Class<?> raw) {
    BigDecimal decimal = new BigDecimal(number.toString());

    try {
      if (raw == int.class || raw == Integer.class) {
        return decimal.intValueExact();
      } else if (raw == long.class || raw == Long.class) {
        return decimal.longValueExact();
      } else if (raw == short.class || raw == Short.class) {
        return decimal.shortValueExact();
      } else if (raw == byte.class || raw == Byte.class) {
        return decimal.byteValueExact();
      } else if (raw == double.class || raw == Double.class) {
        return decimal.doubleValue();
      } else if (raw == float.class || raw == Float.class) {
        return decimal.floatValue();
      } else if (raw == BigInteger.class) {
        return decimal.toBigIntegerExact();
      }

      return decimal;
    } catch (ArithmeticException e) {
      throw new JsonException("Number " + number + " does not fit " + raw.getSimpleName(), e);
    }
  }

  private static Object toArray(List<?> list, Type componentType) {
    Object array = Array.newInstance(rawClass(componentType), list.size());
    for (int i = 0; i < list.size(); i++) {
      Array.set(array, i, convert(list.get(i), componentType));
    }

    return array;
  }

  private static Collection<Object> toCollection(List<?> list, Class<?> raw, Type elementType) {
    Collection<Object> collection = Set.class.isAssignableFrom(raw)
        ? new LinkedHashSet<>()
        : new ArrayList<>();

    for (Object element : list) {
      collection.add(convert(element, elementType));
    }

    return collection;
  }

  private static Map<Object, Object> toMap(Map<?, ?> map, Type keyType, Type valueType) {
    Map<Object, Object> result = new LinkedHashMap<>();
    map.forEach((key, value) ->
        result.put(convert(key, keyType), convert(value, valueType)));

    return result;
  }

  private static Object toRecord(Map<?, ?> map, Class<?> raw) {
    RecordComponent[] components = raw.getRecordComponents();
    Class<?>[] types = new Class<?>[components.length];
    Object[] arguments = new Object[components.length];

    for (int i = 0; i < components.length; i++) {
      RecordComponent component = components[i];
      types[i] = component.getType();

      Object node = map.get(component.getName());
      arguments[i] = node == null && types[i].isPrimitive()
          ? PRIMITIVE_DEFAULTS.get(types[i])
          : convertProperty(node, component.getGenericType(), component.getName());
    }

    try {
      Constructor<?> constructor = raw.getDeclaredConstructor(types);
      constructor.setAccessible(true);

      return constructor.newInstance(arguments);
    } catch (ReflectiveOperationException e) {
      throw new JsonException("Cannot create " + raw.getSimpleName(), e);
    }
  }

  private static Object toBean(Map<?, ?> map, Class<?> raw) {
    Object bean;
    try {
      Constructor<?> constructor = raw.getDeclaredConstructor();
      constructor.setAccessible(true);
      bean = constructor.newInstance();
    } catch (ReflectiveOperationException e) {
      throw new JsonException(raw.getSimpleName() + " needs a no-argument constructor", e);
    }

    for (Class<?> current = raw; current != Object.class; current = current.getSuperclass()) {
      for (Field field : current.getDeclaredFields()) {
        int modifiers = field.getModifiers();
        boolean skipped = Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers);
        if (skipped || !map.containsKey(field.getName())) {
          continue;
        }

        Object value = convertProperty(map.get(field.getName()), field.getGenericType(),
            field.getName());

        try {
          field.setAccessible(true);
          field.set(bean, value);
        } catch (IllegalAccessException e) {
          throw new JsonException("Cannot set field " + field.getName(), e);
        }
      }
    }

    return bean;
  }

  private static Object convertProperty(Object node, Type type, String name) {
    try {
      return convert(node, type);
    } catch (JsonException e) {
      throw new JsonException("Invalid value for '" + name + "': " + e.getMessage(), e);
    }
  }

  private static Class<?> rawClass(Type type) {
    if (type instanceof Class<?> clazz) {
      return clazz;
    }

    if (type instanceof ParameterizedType parameterized) {
      return (Class<?>) parameterized.getRawType();
    }

    if (type instanceof GenericArrayType array) {
      return Array.newInstance(rawClass(array.getGenericComponentType()), 0).getClass();
    }

    if (type instanceof WildcardType wildcard) {
      return rawClass(wildcard.getUpperBounds()[0]);
    }

    if (type instanceof TypeVariable<?> variable) {
      return rawClass(variable.getBounds()[0]);
    }

    return Object.class;
  }

  private static Type componentType(Type type) {
    if (type instanceof GenericArrayType array) {
      return array.getGenericComponentType();
    }

    return rawClass(type).getComponentType();
  }

  private static Type typeArgument(Type type, int index) {
    if (type instanceof ParameterizedType parameterized) {
      return parameterized.getActualTypeArguments()[index];
    }

    return Object.class;
  }

  private static String describe(Object node) {
    if (node instanceof Map<?, ?>) {
      return "object";
    }

    if (node instanceof List<?>) {
      return "array";
    }

    return node.getClass().getSimpleName().toLowerCase(Locale.ROOT);
  }

}
