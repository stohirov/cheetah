package core.error;

import core.handler.Handler;
import http.HttpException;
import http.HttpRequest;
import http.HttpResponse;
import http.HttpStatus;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ErrorHandlingHandler implements Handler {

  private static final Logger LOGGER = Logger.getLogger(ErrorHandlingHandler.class.getName());

  private final Handler delegate;
  private final ExceptionHandlers exceptionHandlers;

  public ErrorHandlingHandler(Handler delegate, ExceptionHandlers exceptionHandlers) {
    this.delegate = delegate;
    this.exceptionHandlers = exceptionHandlers;
  }

  @Override
  public HttpResponse handle(HttpRequest request) {
    try {
      return delegate.handle(request);
    } catch (Exception e) {
      return translate(e, request);
    }
  }

  private HttpResponse translate(Exception exception, HttpRequest request) {
    try {
      Optional<HttpResponse> handled = exceptionHandlers.handle(exception, request);
      if (handled.isPresent()) {
        return handled.get();
      }
    } catch (Exception handlerFailure) {
      LOGGER.log(Level.SEVERE, "Exception handler failed", handlerFailure);

      return internalError(request);
    }

    if (exception instanceof HttpException httpException) {
      return ErrorBody.response(httpException, request.path());
    }

    LOGGER.log(Level.SEVERE,
        "Unhandled error for " + request.method() + " " + request.path(), exception);

    return internalError(request);
  }

  private static HttpResponse internalError(HttpRequest request) {
    return ErrorBody.response(HttpStatus.INTERNAL_SERVER_ERROR, null, request.path());
  }

}
