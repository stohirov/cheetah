package demo;

public class TodoLimitException extends RuntimeException {

  public TodoLimitException(int limit) {
    super("Todo limit of " + limit + " reached");
  }
}
