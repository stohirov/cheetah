package sampleapp.api;

import annotations.Controller;
import annotations.GetMethod;
import annotations.PathVariable;
import java.util.Map;

@Controller(path = "/greetings")
class GreetingController {

  @GetMethod(path = "/{name}")
  Map<String, String> greet(@PathVariable String name) {
    return Map.of("message", "Hello, " + name);
  }
}
