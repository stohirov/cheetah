package sampleapp.api;

import annotations.GetMethod;

class NotAController {

  @GetMethod(path = "/hidden")
  String hidden() {
    return "hidden";
  }
}
