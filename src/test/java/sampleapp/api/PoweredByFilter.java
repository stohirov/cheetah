package sampleapp.api;

import annotations.Component;
import core.filter.Filter;
import core.filter.FilterChain;
import http.HttpRequest;
import http.HttpResponse;

@Component
class PoweredByFilter implements Filter {

  @Override
  public HttpResponse filter(HttpRequest request, FilterChain chain) throws Exception {
    return chain.next(request).withHeader("X-Powered-By", "Cheetah");
  }
}
