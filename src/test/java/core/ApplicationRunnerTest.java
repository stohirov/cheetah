package core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import sampleapp.SampleApp;

class ApplicationRunnerTest {

  private final HttpClient client = HttpClient.newHttpClient();

  @Test
  void servesScannedControllers() throws Exception {
    try (CheetahServer server = ApplicationRunner.run(SampleApp.class, "--server.port=0",
        "--greeting.prefix=Salom")) {
      HttpResponse<String> greeting = get(server, "/greetings/Cheetah");
      HttpResponse<String> hidden = get(server, "/hidden");

      assertEquals(200, greeting.statusCode());
      assertEquals("{\"message\":\"Salom, Cheetah\"}", greeting.body());
      assertEquals(404, hidden.statusCode());
      assertEquals(Optional.of("Cheetah"), greeting.headers().firstValue("X-Powered-By"));
      assertEquals(Optional.of("Cheetah"), hidden.headers().firstValue("X-Powered-By"));

      String first = get(server, "/instance").body();
      String second = get(server, "/instance").body();

      assertTrue(first.endsWith(":/instance"));
      assertNotEquals(first, second);
    }
  }

  @Test
  void keepsSessionsAcrossRequests() throws Exception {
    HttpClient browser = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

    try (CheetahServer server = ApplicationRunner.run(SampleApp.class, "--server.port=0")) {
      URI visits = URI.create("http://localhost:" + server.port() + "/visits");

      assertEquals("1", browser.send(HttpRequest.newBuilder(visits).build(),
          BodyHandlers.ofString()).body());
      assertEquals("2", browser.send(HttpRequest.newBuilder(visits).build(),
          BodyHandlers.ofString()).body());

      browser.send(HttpRequest.newBuilder(visits).DELETE().build(), BodyHandlers.ofString());

      assertEquals("1", browser.send(HttpRequest.newBuilder(visits).build(),
          BodyHandlers.ofString()).body());
    }
  }

  private HttpResponse<String> get(CheetahServer server, String path) throws Exception {
    URI uri = URI.create("http://localhost:" + server.port() + path);

    return client.send(HttpRequest.newBuilder(uri).build(), BodyHandlers.ofString());
  }

}
