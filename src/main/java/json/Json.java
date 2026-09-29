package json;

import java.lang.reflect.Type;

public final class Json {

  private Json() {
  }

  public static String write(Object value) {
    return JsonWriter.write(value);
  }

  public static Object parse(String text) {
    return JsonParser.parse(text);
  }

  public static Object read(String text, Type type) {
    return JsonMapper.convert(parse(text), type);
  }

  @SuppressWarnings("unchecked")
  public static <T> T read(String text, Class<T> type) {
    return (T) read(text, (Type) type);
  }

}
