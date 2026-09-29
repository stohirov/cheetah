package demo;

import annotations.Controller;
import annotations.DeleteMethod;
import annotations.GetMethod;
import annotations.PutMethod;
import annotations.ReqBody;
import core.session.Session;
import core.session.SessionHolder;
import java.util.Map;
import java.util.Optional;

@Controller(path = "/session")
public class SessionController {

  @GetMethod
  public Map<String, String> current(Optional<Session> session) {
    String name = session.flatMap(existing ->
        existing.attribute(CurrentUser.NAME_ATTRIBUTE, String.class)).orElse("anonymous");

    return Map.of("name", name);
  }

  @PutMethod(consumes = "application/json")
  public Map<String, String> signIn(@ReqBody NameRequest request) {
    SessionHolder holder = SessionHolder.current();
    holder.existing().ifPresent(Session::invalidate);

    CurrentUser.rename(holder.getOrCreate(), request.name().trim());

    return Map.of("name", request.name().trim());
  }

  @DeleteMethod
  public void signOut(Optional<Session> session) {
    session.ifPresent(Session::invalidate);
  }

}
