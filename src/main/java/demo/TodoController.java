package demo;

import annotations.Controller;
import annotations.DeleteMethod;
import annotations.GetMethod;
import annotations.PatchMethod;
import annotations.PathVariable;
import annotations.PostMethod;
import annotations.PutMethod;
import annotations.ReqBody;
import annotations.ReqParam;
import http.HttpStatus;
import java.util.List;
import java.util.Optional;

@Controller(path = "/todos")
public class TodoController {

  private final TodoService todoService;

  public TodoController(TodoService todoService) {
    this.todoService = todoService;
  }

  @GetMethod
  public List<Todo> list(@ReqParam Optional<Boolean> done) {
    return todoService.list(done);
  }

  @GetMethod(path = "/{id}")
  public Todo get(@PathVariable long id) {
    return todoService.find(id);
  }

  @PostMethod(consumes = "application/json", status = HttpStatus.CREATED)
  public Todo create(@ReqBody TodoRequest request) {
    return todoService.create(request);
  }

  @PutMethod(path = "/{id}", consumes = "application/json")
  public Todo update(@PathVariable long id, @ReqBody TodoRequest request) {
    return todoService.update(id, request);
  }

  @PatchMethod(path = "/{id}/toggle")
  public Todo toggle(@PathVariable long id) {
    return todoService.toggle(id);
  }

  @DeleteMethod(path = "/{id}")
  public void delete(@PathVariable long id) {
    todoService.delete(id);
  }

}
