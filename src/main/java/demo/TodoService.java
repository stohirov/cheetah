package demo;

import annotations.Component;
import annotations.Value;
import core.context.Provider;
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
  private final int maxItems;
  private final Provider<CurrentUser> currentUser;

  public TodoService(@Value("todo.max-items:500") int maxItems,
      Provider<CurrentUser> currentUser) {
    this.maxItems = maxItems;
    this.currentUser = currentUser;
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
    if (todos.size() >= maxItems) {
      throw new TodoLimitException(maxItems);
    }

    long id = ids.incrementAndGet();
    Todo todo = new Todo(id, request.title().trim(), request.done(), currentUser.get().name());

    todos.put(id, todo);

    return todo;
  }

  public Todo update(long id, TodoRequest request) {
    Todo updated = todos.computeIfPresent(id, (key, todo) ->
        new Todo(id, request.title().trim(), request.done(), todo.createdBy()));

    if (updated == null) {
      throw new TodoNotFoundException(id);
    }

    return updated;
  }

  public Todo toggle(long id) {
    Todo toggled = todos.computeIfPresent(id,
        (key, todo) -> new Todo(todo.id(), todo.title(), !todo.done(), todo.createdBy()));

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

}
