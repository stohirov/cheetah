package sampleapp.api;

import annotations.Controller;
import annotations.DeleteMethod;
import annotations.GetMethod;
import core.session.Session;
import java.util.Optional;

@Controller(path = "/visits")
class VisitController {

  @GetMethod
  int visit(Session session) {
    int visits = session.attribute("visits", Integer.class).orElse(0) + 1;
    session.setAttribute("visits", visits);

    return visits;
  }

  @DeleteMethod
  void reset(Optional<Session> session) {
    session.ifPresent(Session::invalidate);
  }
}
