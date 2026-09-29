package core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import org.junit.jupiter.api.Test;
import sampleapp.SampleApp;

class ApplicationRunnerTest {

  private final HttpClient client = HttpClient.newHttpClient();

  @Test
  void servesScannedControllers() throws Exception {
    try (CheetahServer server = ApplicationRunner.run(SampleApp.class, "--server.port=0")) {
      HttpResponse<String> greeting = get(server, "/greetings/Cheetah");
      HttpResponse<String> hidden = get(server, "/hidden");

      assertEquals(200, greeting.statusCode());
      assertEquals("{\"message\":\"Hello, Cheetah\"}", greeting.body());
      assertEquals(404, hidden.statusCode());
    }
  }

  private HttpResponse<String> get(CheetahServer server, String path) throws Exception {
    URI uri = URI.create("http://localhost:" + server.port() + path);

    return client.send(HttpRequest.newBuilder(uri).build(), BodyHandlers.ofString());
  }

}
