package validation;

import http.HttpException;
import http.HttpStatus;
import java.util.List;

public class ConstraintViolationException extends HttpException {

  private final List<Violation> violations;

  public ConstraintViolationException(List<Violation> violations) {
    super(HttpStatus.BAD_REQUEST, "Validation failed");
    this.violations = List.copyOf(violations);
  }

  public List<Violation> violations() {
    return violations;
  }
}
