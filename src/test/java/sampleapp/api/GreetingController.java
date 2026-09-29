package sampleapp.api;

import annotations.Controller;
import annotations.GetMethod;
import annotations.PathVariable;
import java.util.Map;

@Controller(path = "/greetings")
class GreetingController {

  private final GreetingService greetingService;

  GreetingController(GreetingService greetingService) {
    this.greetingService = greetingService;
  }

  @GetMethod(path = "/{name}")
  Map<String, String> greet(@PathVariable String name) {
    return Map.of("message", greetingService.greet(name));
  }
}
