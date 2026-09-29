package core.filter;

import http.HttpRequest;
import http.HttpResponse;

@FunctionalInterface
public interface FilterChain {

  HttpResponse next(HttpRequest request) throws Exception;

}
