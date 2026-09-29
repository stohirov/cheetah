package demo;

public class TodoNotFoundException extends RuntimeException {

  public TodoNotFoundException(long id) {
    super("Todo " + id + " not found");
  }
}
