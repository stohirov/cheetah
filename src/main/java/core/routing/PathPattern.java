package core.routing;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class PathPattern {

  private final String pattern;
  private final List<String> segments;
  private final Set<String> variables;

  private PathPattern(String pattern, List<String> segments, Set<String> variables) {
    this.pattern = pattern;
    this.segments = segments;
    this.variables = variables;
  }

  public static PathPattern compile(String pattern) {
    List<String> segments = split(pattern);
    Set<String> variables = new LinkedHashSet<>();

    for (String segment : segments) {
      if (isVariable(segment)) {
        String name = variableName(segment);
        if (name.isBlank() || !variables.add(name)) {
          throw new IllegalArgumentException("Invalid path variable in " + pattern);
        }
      } else if (segment.contains("{") || segment.contains("}")) {
        throw new IllegalArgumentException("Path variables must be whole segments: " + pattern);
      }
    }

    return new PathPattern("/" + String.join("/", segments), segments, Set.copyOf(variables));
  }

  public Optional<Map<String, String>> match(String path) {
    List<String> parts = split(path);
    if (parts.size() != segments.size()) {
      return Optional.empty();
    }

    Map<String, String> values = new LinkedHashMap<>();
    for (int i = 0; i < segments.size(); i++) {
      String segment = segments.get(i);
      String part = parts.get(i);

      if (isVariable(segment)) {
        values.put(variableName(segment), part);
      } else if (!segment.equals(part)) {
        return Optional.empty();
      }
    }

    return Optional.of(values);
  }

  public boolean hasVariable(String name) {
    return variables.contains(name);
  }

  public boolean overlaps(PathPattern other) {
    if (segments.size() != other.segments.size()) {
      return false;
    }

    for (int i = 0; i < segments.size(); i++) {
      String mine = segments.get(i);
      String theirs = other.segments.get(i);

      boolean bothVariables = isVariable(mine) && isVariable(theirs);
      if (!bothVariables && !mine.equals(theirs)) {
        return false;
      }
    }

    return true;
  }

  public int compareSpecificity(PathPattern other) {
    int length = Math.min(segments.size(), other.segments.size());

    for (int i = 0; i < length; i++) {
      boolean mine = isVariable(segments.get(i));
      boolean theirs = isVariable(other.segments.get(i));

      if (mine != theirs) {
        return mine ? 1 : -1;
      }
    }

    return 0;
  }

  @Override
  public String toString() {
    return pattern;
  }

  private static List<String> split(String path) {
    return Arrays.stream(path.split("/")).filter(segment -> !segment.isEmpty()).toList();
  }

  private static boolean isVariable(String segment) {
    return segment.startsWith("{") && segment.endsWith("}");
  }

  private static String variableName(String segment) {
    return segment.substring(1, segment.length() - 1);
  }

}
