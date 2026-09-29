package demo;

import annotations.Component;
import annotations.Value;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class TodoService {

  private final Map<Long, Todo> todos = new ConcurrentHashMap<>();
  private final AtomicLong ids = new AtomicLong();
  private final int maxTitleLength;

  public TodoService(@Value("todo.max-title-length:100") int maxTitleLength) {
    this.maxTitleLength = maxTitleLength;
  }

  public List<Todo> list(Optional<Boolean> done) {
    return todos.values().stream()
        .filter(todo -> done.map(value -> todo.done() == value).orElse(true))
        .sorted(Comparator.comparingLong(Todo::id))
        .toList();
  }

  public Todo find(long id) {
    Todo todo = todos.get(id);
    if (todo == null) {
      throw new TodoNotFoundException(id);
    }

    return todo;
  }

  public Todo create(TodoRequest request) {
    long id = ids.incrementAndGet();
    Todo todo = new Todo(id, validTitle(request), request.done());

    todos.put(id, todo);

    return todo;
  }

  public Todo update(long id, TodoRequest request) {
    Todo updated = new Todo(id, validTitle(request), request.done());

    if (todos.replace(id, updated) == null) {
      throw new TodoNotFoundException(id);
    }

    return updated;
  }

  public Todo toggle(long id) {
    Todo toggled = todos.computeIfPresent(id,
        (key, todo) -> new Todo(todo.id(), todo.title(), !todo.done()));

    if (toggled == null) {
      throw new TodoNotFoundException(id);
    }

    return toggled;
  }

  public void delete(long id) {
    if (todos.remove(id) == null) {
      throw new TodoNotFoundException(id);
    }
  }

  private String validTitle(TodoRequest request) {
    String title = request.title() == null ? "" : request.title().trim();

    if (title.isEmpty()) {
      throw new ValidationException("title is required");
    }

    if (title.length() > maxTitleLength) {
      throw new ValidationException("title must be at most " + maxTitleLength + " characters");
    }

    return title;
  }

}
