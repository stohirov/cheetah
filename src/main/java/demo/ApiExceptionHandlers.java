package demo;

import annotations.Component;
import annotations.ExceptionHandler;
import core.error.ErrorBody;
import http.HttpRequest;
import http.HttpStatus;

@Component
public class ApiExceptionHandlers {

  @ExceptionHandler(status = HttpStatus.NOT_FOUND)
  public ErrorBody notFound(TodoNotFoundException exception, HttpRequest request) {
    return body(HttpStatus.NOT_FOUND, exception, request);
  }

  @ExceptionHandler(status = HttpStatus.CONFLICT)
  public ErrorBody limitReached(TodoLimitException exception, HttpRequest request) {
    return body(HttpStatus.CONFLICT, exception, request);
  }

  private static ErrorBody body(HttpStatus status, Exception exception, HttpRequest request) {
    return new ErrorBody(status.code(), status.reason(), exception.getMessage(), request.path());
  }

}
