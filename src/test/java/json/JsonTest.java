package json;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JsonTest {

  enum Role {
    ADMIN,
    USER
  }

  record Address(String city, int zip) {
  }

  record User(long id, String name, Role role, List<Address> addresses, Set<String> tags,
      LocalDate joined, boolean active) {
  }

  static class Note {

    private String title;
    private int[] scores;

    public String getTitle() {
      return title;
    }

    public int[] getScores() {
      return scores;
    }
  }

  @Test
  void writesRecordsCollectionsAndScalars() {
    User user = new User(1, "Ann \"A\"\n", Role.ADMIN, List.of(new Address("Tashkent", 100000)),
        Set.of("x"), LocalDate.of(2026, 9, 29), true);

    assertEquals(
        "{\"id\":1,\"name\":\"Ann \\\"A\\\"\\n\",\"role\":\"ADMIN\","
            + "\"addresses\":[{\"city\":\"Tashkent\",\"zip\":100000}],\"tags\":[\"x\"],"
            + "\"joined\":\"2026-09-29\",\"active\":true}",
        Json.write(user));
  }

  @Test
  void writesMapsArraysAndNull() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("empty", null);
    map.put("numbers", new int[] {1, 2});
    map.put("ratio", 0.5);

    assertEquals("{\"empty\":null,\"numbers\":[1,2],\"ratio\":0.5}", Json.write(map));
  }

  @Test
  void writesBeanGetters() {
    Note note = new Note();
    note.title = "todo";
    note.scores = new int[] {3};

    assertEquals("{\"scores\":[3],\"title\":\"todo\"}", sortedKeys(Json.write(note)));
  }

  @Test
  void rejectsCyclicGraphs() {
    List<Object> cycle = new java.util.ArrayList<>();
    cycle.add(cycle);

    assertThrows(JsonException.class, () -> Json.write(cycle));
  }

  @Test
  void parsesIntoTree() {
    Object tree = Json.parse(" {\"a\": [1, 2.5, -3e2, true, null, \"\\u0041\\t\"], \"b\": {}} ");

    assertEquals(
        Map.of("a", java.util.Arrays.asList(1L, 2.5, -300.0, true, null, "A\t"), "b", Map.of()),
        tree);
  }

  @Test
  void readsNestedRecords() {
    User user = Json.read(
        "{\"id\": 7, \"name\": \"Bob\", \"role\": \"user\", \"tags\": [\"a\", \"a\"],"
            + "\"addresses\": [{\"city\": \"Samarkand\", \"zip\": 140100}],"
            + "\"joined\": \"2026-01-02\", \"unknown\": 1}",
        User.class);

    assertEquals(7, user.id());
    assertEquals(Role.USER, user.role());
    assertEquals(List.of(new Address("Samarkand", 140100)), user.addresses());
    assertEquals(Set.of("a"), user.tags());
    assertEquals(LocalDate.of(2026, 1, 2), user.joined());
    assertEquals(false, user.active());
  }

  @Test
  void readsBeansByField() {
    Note note = Json.read("{\"title\": \"t\", \"scores\": [1, 2]}", Note.class);

    assertEquals("t", note.getTitle());
    assertArrayEquals(new int[] {1, 2}, note.getScores());
  }

  @Test
  void readsMissingReferenceAsNull() {
    assertNull(Json.read("{\"id\": 1}", User.class).name());
  }

  @Test
  void rejectsMalformedJson() {
    assertThrows(JsonException.class, () -> Json.parse("{\"a\": 1,}"));
    assertThrows(JsonException.class, () -> Json.parse("[1] 2"));
    assertThrows(JsonException.class, () -> Json.parse("01"));
    assertThrows(JsonException.class, () -> Json.parse("\"open"));
    assertThrows(JsonException.class, () -> Json.parse("[".repeat(1000)));
  }

  @Test
  void rejectsTypeMismatches() {
    assertThrows(JsonException.class, () -> Json.read("{\"id\": \"x\"}", User.class));
    assertThrows(JsonException.class, () -> Json.read("{\"id\": 1.5}", User.class));
    assertThrows(JsonException.class, () -> Json.read("[1]", User.class));
  }

  private static String sortedKeys(String json) {
    @SuppressWarnings("unchecked")
    Map<String, Object> map = new java.util.TreeMap<>((Map<String, Object>) Json.parse(json));

    return Json.write(map);
  }

}
