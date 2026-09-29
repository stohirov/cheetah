package core.context;

import core.handler.Handler;
import http.HttpRequest;
import http.HttpResponse;

public class RequestScopeHandler implements Handler {

  private final Handler delegate;

  public RequestScopeHandler(Handler delegate) {
    this.delegate = delegate;
  }

  @Override
  public HttpResponse handle(HttpRequest request) throws Exception {
    try (RequestContext context = RequestContext.open(request)) {
      return delegate.handle(request);
    }
  }

}
