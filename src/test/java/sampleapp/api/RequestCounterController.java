package sampleapp.api;

import annotations.Controller;
import annotations.GetMethod;
import annotations.RequestScoped;
import http.HttpRequest;
import java.util.concurrent.atomic.AtomicInteger;

@Controller
@RequestScoped
class RequestCounterController {

  private static final AtomicInteger INSTANCES = new AtomicInteger();

  private final int instance;
  private final String path;

  RequestCounterController(HttpRequest request) {
    this.instance = INSTANCES.incrementAndGet();
    this.path = request.path();
  }

  @GetMethod(path = "/instance")
  String instance() {
    return instance + ":" + path;
  }
}
