package demo;

import annotations.Component;
import annotations.RequestScoped;
import core.session.Session;
import core.session.SessionHolder;

@Component
@RequestScoped
public class CurrentUser {

  static final String NAME_ATTRIBUTE = "name";

  private final String name;

  public CurrentUser() {
    this.name = SessionHolder.current().existing()
        .flatMap(session -> session.attribute(NAME_ATTRIBUTE, String.class))
        .orElse("anonymous");
  }

  public String name() {
    return name;
  }

  static void rename(Session session, String name) {
    session.setAttribute(NAME_ATTRIBUTE, name);
  }
}
