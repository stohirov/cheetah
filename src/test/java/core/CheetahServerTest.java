package core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import core.handler.Handler;
import http.HttpException;
import http.HttpResponse;
import http.HttpStatus;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import models.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CheetahServerTest {

  private final HttpClient client = HttpClient.newHttpClient();

  private CheetahServer server;

  @AfterEach
  void stopServer() throws IOException {
    if (server != null) {
      server.close();
    }
  }

  @Test
  void servesHandlerResponse() throws Exception {
    start(request -> HttpResponse.text(HttpStatus.OK,
        request.method() + " " + request.path() + " " + request.bodyAsString()));

    java.net.http.HttpResponse<String> response = client.send(
        HttpRequest.newBuilder(uri("/echo"))
            .POST(HttpRequest.BodyPublishers.ofString("hello"))
            .build(),
        BodyHandlers.ofString());

    assertEquals(200, response.statusCode());
    assertEquals("POST /echo hello", response.body());
  }

  @Test
  void mapsHttpExceptionToStatus() throws Exception {
    start(request -> {
      throw new HttpException(HttpStatus.NOT_FOUND, "nothing here");
    });

    java.net.http.HttpResponse<String> response = get("/missing");

    assertEquals(404, response.statusCode());
    assertEquals("{\"status\":404,\"error\":\"Not Found\",\"message\":\"nothing here\","
        + "\"path\":\"/missing\"}", response.body());
  }

  @Test
  void hidesUnexpectedErrors() throws Exception {
    start(request -> {
      throw new IllegalStateException("secret detail");
    });

    java.net.http.HttpResponse<String> response = get("/boom");

    assertEquals(500, response.statusCode());
    assertFalse(response.body().contains("secret detail"));
  }

  @Test
  void servesMultipleRequestsOnOneConnection() throws Exception {
    start(request -> HttpResponse.text(HttpStatus.OK, request.path()));

    try (Socket socket = new Socket("localhost", server.port())) {
      OutputStream output = socket.getOutputStream();
      output.write(("GET /a HTTP/1.1\r\n\r\nGET /b HTTP/1.1\r\nConnection: close\r\n\r\n")
          .getBytes(StandardCharsets.US_ASCII));
      output.flush();

      String raw = readAll(socket.getInputStream());

      assertTrue(raw.contains("Connection: keep-alive\r\n\r\n/a"));
      assertTrue(raw.endsWith("Connection: close\r\n\r\n/b"));
    }
  }

  @Test
  void omitsBodyForHeadRequests() throws Exception {
    start(request -> HttpResponse.text(HttpStatus.OK, "payload"));

    try (Socket socket = new Socket("localhost", server.port())) {
      socket.getOutputStream().write("HEAD / HTTP/1.1\r\nConnection: close\r\n\r\n"
          .getBytes(StandardCharsets.US_ASCII));

      String raw = readAll(socket.getInputStream());

      assertTrue(raw.contains("Content-Length: 7\r\n"));
      assertFalse(raw.contains("payload"));
    }
  }

  @Test
  void rejectsMalformedRequest() throws Exception {
    start(request -> HttpResponse.of(HttpStatus.OK));

    try (Socket socket = new Socket("localhost", server.port())) {
      socket.getOutputStream().write("nonsense\r\n\r\n".getBytes(StandardCharsets.US_ASCII));

      String raw = readAll(socket.getInputStream());

      assertTrue(raw.startsWith("HTTP/1.1 400 Bad Request\r\n"));
    }
  }

  private void start(Handler handler) throws IOException {
    server = new CheetahServer(new Server(0), handler);
    server.start();
  }

  private java.net.http.HttpResponse<String> get(String path) throws Exception {
    return client.send(HttpRequest.newBuilder(uri(path)).build(), BodyHandlers.ofString());
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + server.port() + path);
  }

  private static String readAll(InputStream input) throws IOException {
    return new String(input.readAllBytes(), StandardCharsets.UTF_8);
  }

}
