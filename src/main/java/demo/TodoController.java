package demo;

import annotations.Controller;
import annotations.DeleteMethod;
import annotations.GetMethod;
import annotations.PathVariable;
import annotations.PostMethod;
import annotations.PutMethod;
import annotations.ReqBody;
import annotations.ReqParam;
import http.HttpException;
import http.HttpResponse;
import http.HttpStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import json.Json;

@Controller(path = "/todos")
public class TodoController {

  private final Map<Long, Todo> todos = new ConcurrentHashMap<>();
  private final AtomicLong ids = new AtomicLong();

  @GetMethod
  public List<Todo> list(@ReqParam Optional<Boolean> done) {
    return todos.values().stream()
        .filter(todo -> done.map(value -> todo.done() == value).orElse(true))
        .sorted(Comparator.comparingLong(Todo::id))
        .toList();
  }

  @GetMethod(path = "/{id}")
  public Todo get(@PathVariable long id) {
    return find(id);
  }

  @PostMethod(consumes = "application/json")
  public HttpResponse create(@ReqBody TodoRequest request) {
    long id = ids.incrementAndGet();
    Todo todo = new Todo(id, requireTitle(request), request.done());

    todos.put(id, todo);

    return HttpResponse.json(HttpStatus.CREATED, Json.write(todo))
        .withHeader("Location", "/todos/" + id);
  }

  @PutMethod(path = "/{id}", consumes = "application/json")
  public Todo update(@PathVariable long id, @ReqBody TodoRequest request) {
    Todo updated = new Todo(id, requireTitle(request), request.done());

    if (todos.replace(id, updated) == null) {
      throw notFound(id);
    }

    return updated;
  }

  @DeleteMethod(path = "/{id}")
  public void delete(@PathVariable long id) {
    if (todos.remove(id) == null) {
      throw notFound(id);
    }
  }

  private Todo find(long id) {
    Todo todo = todos.get(id);
    if (todo == null) {
      throw notFound(id);
    }

    return todo;
  }

  private static String requireTitle(TodoRequest request) {
    if (request.title() == null || request.title().isBlank()) {
      throw new HttpException(HttpStatus.BAD_REQUEST, "title is required");
    }

    return request.title().trim();
  }

  private static HttpException notFound(long id) {
    return new HttpException(HttpStatus.NOT_FOUND, "Todo " + id + " not found");
  }

}
