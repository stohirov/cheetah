package core.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import annotations.RequestScoped;
import config.Config;
import http.HttpMethod;
import http.HttpRequest;
import http.HttpResponse;
import http.HttpStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RequestScopeTest {

  static final List<String> CLOSED = new ArrayList<>();

  @RequestScoped
  static class RequestInfo implements AutoCloseable {

    final HttpRequest request;

    RequestInfo(HttpRequest request) {
      this.request = request;
    }

    @Override
    public void close() {
      CLOSED.add(request.path());
    }
  }

  static class Singleton {

    final Provider<RequestInfo> info;

    Singleton(Provider<RequestInfo> info) {
      this.info = info;
    }
  }

  static class BrokenSingleton {

    BrokenSingleton(RequestInfo info) {
    }
  }

  @RequestScoped
  static class NeedsMissingDependency {

    NeedsMissingDependency(Runnable missing) {
    }
  }

  private final ApplicationContext context = new ApplicationContext(new Config("test", Map.of()),
      List.of(RequestInfo.class, Singleton.class));

  @Test
  void createsOneInstancePerRequestAndClosesIt() throws Exception {
    context.refresh();
    CLOSED.clear();

    List<RequestInfo> seen = new ArrayList<>();
    RequestScopeHandler handler = new RequestScopeHandler(request -> {
      RequestInfo first = context.getBean(RequestInfo.class);
      assertSame(first, context.getBean(Singleton.class).info.get());
      seen.add(first);

      return HttpResponse.of(HttpStatus.OK);
    });

    handler.handle(request("/one"));
    handler.handle(request("/two"));

    assertNotSame(seen.get(0), seen.get(1));
    assertEquals("/one", seen.get(0).request.path());
    assertEquals(List.of("/one", "/two"), CLOSED);
    assertTrue(RequestContext.find().isEmpty());
  }

  @Test
  void refusesRequestBeansOutsideRequests() {
    context.refresh();

    assertThrows(BeanException.class, () -> context.getBean(RequestInfo.class));
  }

  @Test
  void refusesDirectInjectionIntoSingletons() {
    ApplicationContext broken = new ApplicationContext(new Config("test", Map.of()),
        List.of(RequestInfo.class, BrokenSingleton.class));

    BeanException exception = assertThrows(BeanException.class, broken::refresh);

    assertTrue(exception.getMessage().contains("Provider<RequestInfo>"));
  }

  @Test
  void verifiesRequestScopedDependenciesAtStartup() {
    ApplicationContext broken = new ApplicationContext(new Config("test", Map.of()),
        List.of(NeedsMissingDependency.class));

    assertThrows(BeanException.class, broken::refresh);
  }

  @Test
  void exposesRequestAttributes() throws Exception {
    new RequestScopeHandler(request -> {
      RequestContext.current().setAttribute("user", "ann");

      assertEquals("ann", RequestContext.current().attribute("user").orElseThrow());

      return HttpResponse.of(HttpStatus.OK);
    }).handle(request("/"));
  }

  private static HttpRequest request(String path) {
    return new HttpRequest(HttpMethod.GET, path, "HTTP/1.1", Map.of(), Map.of(), new byte[0]);
  }

}
