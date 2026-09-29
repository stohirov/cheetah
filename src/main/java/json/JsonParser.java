package json;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JsonParser {

  private static final int MAX_DEPTH = 256;

  private final String text;
  private int position;
  private int depth;

  private JsonParser(String text) {
    this.text = text;
  }

  static Object parse(String text) {
    JsonParser parser = new JsonParser(text);
    Object value = parser.readValue();

    parser.skipWhitespace();
    if (parser.position != text.length()) {
      throw parser.error("Unexpected trailing content");
    }

    return value;
  }

  private Object readValue() {
    skipWhitespace();
    if (position >= text.length()) {
      throw error("Unexpected end of input");
    }

    char current = text.charAt(position);

    return switch (current) {
      case '{' -> readObject();
      case '[' -> readArray();
      case '"' -> readString();
      case 't' -> readLiteral("true", Boolean.TRUE);
      case 'f' -> readLiteral("false", Boolean.FALSE);
      case 'n' -> readLiteral("null", null);
      default -> {
        if (current == '-' || Character.isDigit(current)) {
          yield readNumber();
        }

        throw error("Unexpected character '" + current + "'");
      }
    };
  }

  private Map<String, Object> readObject() {
    enter();
    position++;

    Map<String, Object> object = new LinkedHashMap<>();

    skipWhitespace();
    if (peek() == '}') {
      position++;
      depth--;

      return object;
    }

    while (true) {
      skipWhitespace();
      if (peek() != '"') {
        throw error("Expected object key");
      }

      String key = readString();

      skipWhitespace();
      expect(':');

      object.put(key, readValue());

      skipWhitespace();
      if (peek() == ',') {
        position++;
        continue;
      }

      expect('}');
      depth--;

      return object;
    }
  }

  private List<Object> readArray() {
    enter();
    position++;

    List<Object> array = new ArrayList<>();

    skipWhitespace();
    if (peek() == ']') {
      position++;
      depth--;

      return array;
    }

    while (true) {
      array.add(readValue());

      skipWhitespace();
      if (peek() == ',') {
        position++;
        continue;
      }

      expect(']');
      depth--;

      return array;
    }
  }

  private String readString() {
    position++;

    StringBuilder builder = new StringBuilder();
    while (position < text.length()) {
      char current = text.charAt(position++);

      if (current == '"') {
        return builder.toString();
      }

      if (current < 0x20) {
        throw error("Unescaped control character in string");
      }

      if (current != '\\') {
        builder.append(current);
        continue;
      }

      if (position >= text.length()) {
        break;
      }

      char escaped = text.charAt(position++);
      switch (escaped) {
        case '"', '\\', '/' -> builder.append(escaped);
        case 'b' -> builder.append('\b');
        case 'f' -> builder.append('\f');
        case 'n' -> builder.append('\n');
        case 'r' -> builder.append('\r');
        case 't' -> builder.append('\t');
        case 'u' -> builder.append(readUnicodeEscape());
        default -> throw error("Invalid escape '\\" + escaped + "'");
      }
    }

    throw error("Unterminated string");
  }

  private char readUnicodeEscape() {
    if (position + 4 > text.length()) {
      throw error("Truncated unicode escape");
    }

    String hex = text.substring(position, position + 4);
    position += 4;

    try {
      return (char) Integer.parseInt(hex, 16);
    } catch (NumberFormatException e) {
      throw error("Invalid unicode escape '" + hex + "'");
    }
  }

  private Object readLiteral(String literal, Object value) {
    if (!text.startsWith(literal, position)) {
      throw error("Unexpected token");
    }

    position += literal.length();

    return value;
  }

  private Number readNumber() {
    int start = position;
    boolean decimal = false;

    if (peek() == '-') {
      position++;
    }

    position = skipDigits(position);

    if (peek() == '.') {
      decimal = true;
      position = skipDigits(position + 1);
    }

    if (peek() == 'e' || peek() == 'E') {
      decimal = true;
      position++;
      if (peek() == '+' || peek() == '-') {
        position++;
      }

      position = skipDigits(position);
    }

    String number = text.substring(start, position);
    if (!number.matches("-?(0|[1-9]\\d*)(\\.\\d+)?([eE][+-]?\\d+)?")) {
      throw error("Invalid number '" + number + "'");
    }

    if (decimal) {
      return Double.valueOf(number);
    }

    BigInteger integer = new BigInteger(number);

    return integer.bitLength() < Long.SIZE ? (Number) integer.longValue() : integer;
  }

  private int skipDigits(int from) {
    int index = from;
    while (index < text.length() && Character.isDigit(text.charAt(index))) {
      index++;
    }

    return index;
  }

  private void enter() {
    if (++depth > MAX_DEPTH) {
      throw error("Nesting is too deep");
    }
  }

  private void expect(char expected) {
    if (peek() != expected) {
      throw error("Expected '" + expected + "'");
    }

    position++;
  }

  private char peek() {
    return position < text.length() ? text.charAt(position) : '\0';
  }

  private void skipWhitespace() {
    while (position < text.length() && " \t\r\n".indexOf(text.charAt(position)) >= 0) {
      position++;
    }
  }

  private JsonException error(String message) {
    return new JsonException(message + " at position " + position);
  }

}
