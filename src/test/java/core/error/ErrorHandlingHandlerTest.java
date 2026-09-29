package core.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import annotations.ExceptionHandler;
import http.HttpException;
import http.HttpMethod;
import http.HttpRequest;
import http.HttpResponse;
import http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import json.Json;
import org.junit.jupiter.api.Test;
import validation.ConstraintViolationException;
import validation.Violation;

class ErrorHandlingHandlerTest {

  static class NotFoundException extends RuntimeException {

    NotFoundException(String message) {
      super(message);
    }
  }

  static class Handlers {

    @ExceptionHandler(status = HttpStatus.NOT_FOUND)
    Map<String, String> notFound(NotFoundException exception, HttpRequest request) {
      return Map.of("missing", exception.getMessage(), "path", request.path());
    }

    @ExceptionHandler(RuntimeException.class)
    HttpResponse runtime() {
      return HttpResponse.text(HttpStatus.SERVICE_UNAVAILABLE, "try later");
    }

    @ExceptionHandler(IllegalStateException.class)
    void failing(IllegalStateException exception) {
      throw new IllegalArgumentException("handler broke");
    }
  }

  static class InvalidHandlers {

    @ExceptionHandler(Exception.class)
    void wrongParameter(IllegalStateException exception) {
    }
  }

  private final ExceptionHandlers exceptionHandlers = new ExceptionHandlers()
      .register(new Handlers());

  @Test
  void usesMostSpecificHandler() {
    HttpResponse response = handle(new NotFoundException("todo 7"));

    assertEquals(HttpStatus.NOT_FOUND, response.status());
    assertEquals("{\"missing\":\"todo 7\",\"path\":\"/test\"}", sortedBody(response));
  }

  @Test
  void fallsBackToSuperclassHandler() {
    HttpResponse response = handle(new UnsupportedOperationException());

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.status());
  }

  @Test
  void rendersHttpExceptionsAsJsonWithHeaders() {
    ErrorHandlingHandler handler = new ErrorHandlingHandler(request -> {
      throw new HttpException(HttpStatus.METHOD_NOT_ALLOWED, "nope").withHeader("Allow", "GET");
    }, new ExceptionHandlers());

    HttpResponse response = handler.handle(request());

    assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.status());
    assertEquals(Optional.of("GET"), response.header("Allow"));
    assertEquals("{\"status\":405,\"error\":\"Method Not Allowed\",\"message\":\"nope\","
        + "\"path\":\"/test\"}", body(response));
  }

  @Test
  void rendersViolations() {
    ErrorHandlingHandler handler = new ErrorHandlingHandler(request -> {
      throw new ConstraintViolationException(List.of(new Violation("name", "must not be blank")));
    }, new ExceptionHandlers());

    HttpResponse response = handler.handle(request());

    assertEquals(HttpStatus.BAD_REQUEST, response.status());
    assertEquals("{\"status\":400,\"error\":\"Bad Request\",\"message\":\"Validation failed\","
        + "\"path\":\"/test\",\"violations\":[{\"field\":\"name\","
        + "\"message\":\"must not be blank\"}]}", body(response));
  }

  @Test
  void hidesUnhandledErrors() {
    ErrorHandlingHandler handler = new ErrorHandlingHandler(request -> {
      throw new Exception("secret");
    }, new ExceptionHandlers());

    HttpResponse response = handler.handle(request());

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.status());
    assertEquals("{\"status\":500,\"error\":\"Internal Server Error\","
        + "\"message\":\"Internal Server Error\",\"path\":\"/test\"}", body(response));
  }

  @Test
  void returnsInternalErrorWhenHandlerFails() {
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
        handle(new IllegalStateException()).status());
  }

  @Test
  void rejectsIncompatibleHandlerSignature() {
    assertThrows(IllegalArgumentException.class,
        () -> new ExceptionHandlers().register(new InvalidHandlers()));
  }

  private HttpResponse handle(Exception exception) {
    return new ErrorHandlingHandler(request -> {
      throw exception;
    }, exceptionHandlers).handle(request());
  }

  private static HttpRequest request() {
    return new HttpRequest(HttpMethod.GET, "/test", "HTTP/1.1", Map.of(), Map.of(), new byte[0]);
  }

  private static String body(HttpResponse response) {
    return new String(response.body(), StandardCharsets.UTF_8);
  }

  private static String sortedBody(HttpResponse response) {
    @SuppressWarnings("unchecked")
    Map<String, Object> map = (Map<String, Object>) Json.parse(body(response));

    return Json.write(new TreeMap<>(map));
  }

}
