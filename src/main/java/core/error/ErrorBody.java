package core.error;

import http.HttpException;
import http.HttpResponse;
import http.HttpStatus;
import java.util.List;
import json.Json;
import validation.ConstraintViolationException;
import validation.Violation;

public record ErrorBody(int status, String error, String message, String path) {

  public static HttpResponse response(HttpStatus status, String message, String path) {
    ErrorBody body = new ErrorBody(status.code(), status.reason(),
        message == null ? status.reason() : message, path);

    return HttpResponse.json(status, Json.write(body));
  }

  public static HttpResponse response(HttpException exception, String path) {
    HttpResponse response = exception instanceof ConstraintViolationException invalid
        ? validationResponse(invalid, path)
        : response(exception.status(), exception.getMessage(), path);

    for (var header : exception.headers().entrySet()) {
      response = response.withHeader(header.getKey(), header.getValue());
    }

    return response;
  }

  private static HttpResponse validationResponse(ConstraintViolationException exception,
      String path) {
    HttpStatus status = exception.status();
    ValidationErrorBody body = new ValidationErrorBody(status.code(), status.reason(),
        exception.getMessage(), path, exception.violations());

    return HttpResponse.json(status, Json.write(body));
  }

  private record ValidationErrorBody(int status, String error, String message, String path,
      List<Violation> violations) {
  }

}
