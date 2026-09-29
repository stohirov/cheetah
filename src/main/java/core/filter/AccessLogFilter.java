package core.filter;

import annotations.Order;
import http.HttpRequest;
import http.HttpResponse;
import java.util.logging.Logger;

@Order(Integer.MIN_VALUE)
public class AccessLogFilter implements Filter {

  private static final Logger LOGGER = Logger.getLogger(AccessLogFilter.class.getName());

  @Override
  public HttpResponse filter(HttpRequest request, FilterChain chain) throws Exception {
    long start = System.nanoTime();

    HttpResponse response = chain.next(request);

    long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
    LOGGER.info(request.method() + " " + request.path() + " -> " + response.status().code()
        + " (" + elapsedMillis + " ms)");

    return response;
  }

}
