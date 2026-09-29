package core.filter;

import http.HttpRequest;
import http.HttpResponse;

@FunctionalInterface
public interface Filter {

  HttpResponse filter(HttpRequest request, FilterChain chain) throws Exception;

}
