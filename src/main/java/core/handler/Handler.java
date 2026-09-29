package core.handler;

import http.HttpRequest;
import http.HttpResponse;

@FunctionalInterface
public interface Handler {

  HttpResponse handle(HttpRequest request) throws Exception;

}
