package validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ValidatorTest {

  record Address(@NotBlank String city, @Pattern(regexp = "\\d{6}") String zip) {
  }

  record Signup(
      @NotBlank @Size(max = 10) String username,
      @Email String email,
      @Min(18) @Max(130) int age,
      @NotEmpty List<String> roles,
      @Valid @NotNull Address address,
      @Valid List<Address> previous,
      @Size(min = 2, message = "need two tags") String[] tags) {
  }

  static class Profile {

    @NotNull
    private String nickname;

    @Valid
    private Map<String, Address> places = Map.of("home", new Address(" ", "100000"));
  }

  static class Node {

    @Valid
    Node next;

    @NotBlank
    String label = "ok";
  }

  @Test
  void acceptsValidObjects() {
    Signup signup = new Signup("ann", "ann@example.com", 30, List.of("user"),
        new Address("Tashkent", "100000"), List.of(), new String[] {"a", "b"});

    assertEquals(List.of(), Validator.validate(signup));
  }

  @Test
  void reportsEveryViolationWithFieldPaths() {
    Signup signup = new Signup(" ", "not-an-email", 12, List.of(), new Address("", "abc"),
        List.of(new Address("Bukhara", "1")), new String[] {"a"});

    assertEquals(List.of(
        new Violation("username", "must not be blank"),
        new Violation("email", "must be a valid email address"),
        new Violation("age", "must be greater than or equal to 18"),
        new Violation("roles", "must not be empty"),
        new Violation("address.city", "must not be blank"),
        new Violation("address.zip", "must match \"\\d{6}\""),
        new Violation("previous[0].zip", "must match \"\\d{6}\""),
        new Violation("tags", "need two tags")), Validator.validate(signup));
  }

  @Test
  void validatesFieldsAndMaps() {
    assertEquals(List.of(
        new Violation("nickname", "must not be null"),
        new Violation("places[home].city", "must not be blank")),
        Validator.validate(new Profile()));
  }

  @Test
  void survivesCycles() {
    Node node = new Node();
    node.next = node;

    assertEquals(List.of(), Validator.validate(node));
  }

  @Test
  void throwsWhenRequired() {
    ConstraintViolationException exception = assertThrows(ConstraintViolationException.class,
        () -> Validator.requireValid(new Profile()));

    assertEquals(2, exception.violations().size());
  }

  @Test
  void rejectsConstraintsOnUnsupportedTypes() {
    record Wrong(@Size(max = 1) Integer value) {
    }

    assertThrows(IllegalArgumentException.class, () -> Validator.validate(new Wrong(5)));
  }

}
