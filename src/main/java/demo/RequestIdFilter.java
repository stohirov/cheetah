package demo;

import annotations.Component;
import annotations.Order;
import core.filter.Filter;
import core.filter.FilterChain;
import http.HttpRequest;
import http.HttpResponse;
import java.util.UUID;

@Component
@Order(1)
public class RequestIdFilter implements Filter {

  private static final String HEADER = "X-Request-Id";

  @Override
  public HttpResponse filter(HttpRequest request, FilterChain chain) throws Exception {
    String requestId = request.header(HEADER)
        .filter(value -> value.matches("[A-Za-z0-9-]{1,64}"))
        .orElseGet(() -> UUID.randomUUID().toString());

    return chain.next(request).withHeader(HEADER, requestId);
  }

}
