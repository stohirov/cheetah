package core.routing;

import http.HttpResponse;
import http.HttpStatus;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import json.Json;

public final class ResponseConverter {

  private ResponseConverter() {
  }

  public static HttpResponse convert(Object result, HttpStatus status, String produces) {
    if (result instanceof HttpResponse response) {
      return response;
    }

    if (result == null) {
      return HttpResponse.of(status == HttpStatus.OK ? HttpStatus.NO_CONTENT : status);
    }

    HttpResponse response;
    if (result instanceof byte[] bytes) {
      response = new HttpResponse(status, Map.of("Content-Type", "application/octet-stream"),
          bytes);
    } else if (result instanceof CharSequence text) {
      response = HttpResponse.text(status, text.toString());
    } else {
      response = HttpResponse.json(status, Json.write(result));
    }

    return produces.isBlank() ? response : response.withHeader("Content-Type", produces);
  }

  public static Object invoke(Method method, Object target, Object[] arguments) throws Exception {
    try {
      return method.invoke(target, arguments);
    } catch (InvocationTargetException e) {
      if (e.getCause() instanceof Exception cause) {
        throw cause;
      }

      throw (Error) e.getCause();
    }
  }

}
