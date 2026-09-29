package core.error;

import http.HttpException;
import http.HttpResponse;
import http.HttpStatus;
import json.Json;

public record ErrorBody(int status, String error, String message, String path) {

  public static HttpResponse response(HttpStatus status, String message, String path) {
    ErrorBody body = new ErrorBody(status.code(), status.reason(),
        message == null ? status.reason() : message, path);

    return HttpResponse.json(status, Json.write(body));
  }

  public static HttpResponse response(HttpException exception, String path) {
    HttpResponse response = response(exception.status(), exception.getMessage(), path);

    for (var header : exception.headers().entrySet()) {
      response = response.withHeader(header.getKey(), header.getValue());
    }

    return response;
  }

}
