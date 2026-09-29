package sampleapp.api;

import annotations.Component;
import annotations.Value;

@Component
class GreetingService {

  private final String prefix;

  GreetingService(@Value("greeting.prefix:Hello") String prefix) {
    this.prefix = prefix;
  }

  String greet(String name) {
    return prefix + ", " + name;
  }
}
