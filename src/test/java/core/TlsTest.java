package core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sampleapp.SampleApp;

class TlsTest {

  private static final String PASSWORD = "changeit";

  @TempDir
  static Path directory;

  private static Path keyStore;

  @BeforeAll
  static void generateKeyStore() throws Exception {
    keyStore = directory.resolve("cheetah.p12");
    Path keytool = Path.of(System.getProperty("java.home"), "bin", "keytool");

    Process process = new ProcessBuilder(List.of(keytool.toString(),
        "-genkeypair", "-alias", "cheetah", "-keyalg", "EC", "-groupname", "secp256r1",
        "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1", "-validity", "1",
        "-storetype", "PKCS12", "-keystore", keyStore.toString(),
        "-storepass", PASSWORD, "-keypass", PASSWORD, "-noprompt"))
        .redirectErrorStream(true)
        .start();

    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertEquals(0, process.waitFor(), output);
  }

  @Test
  void servesHttpsWithSecureSessionCookies() throws Exception {
    try (CheetahServer server = startSecureServer()) {
      HttpResponse<String> response = trustingClient().send(
          HttpRequest.newBuilder(URI.create("https://localhost:" + server.port() + "/visits"))
              .build(),
          BodyHandlers.ofString());

      assertEquals(200, response.statusCode());
      assertEquals("1", response.body());
      assertTrue(response.headers().firstValue("Set-Cookie").orElseThrow().contains("; Secure"));
    }
  }

  @Test
  void rejectsUntrustedClientsAndPlainHttp() throws Exception {
    try (CheetahServer server = startSecureServer()) {
      URI uri = URI.create("https://localhost:" + server.port() + "/visits");

      assertThrows(IOException.class, () -> HttpClient.newHttpClient()
          .send(HttpRequest.newBuilder(uri).build(), BodyHandlers.ofString()));

      try (Socket socket = new Socket("localhost", server.port())) {
        socket.getOutputStream()
            .write("GET / HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.US_ASCII));

        InputStream input = socket.getInputStream();
        byte[] reply = input.readNBytes(4);

        assertTrue(reply.length == 0 || !new String(reply, StandardCharsets.US_ASCII)
            .startsWith("HTTP"));
      }

      assertEquals(200, trustingClient()
          .send(HttpRequest.newBuilder(uri).build(), BodyHandlers.ofString())
          .statusCode());
    }
  }

  @Test
  void failsFastOnMissingKeyStore() {
    assertThrows(RuntimeException.class, () -> ApplicationRunner.run(SampleApp.class,
        "--server.port=0", "--server.ssl.enabled=true",
        "--server.ssl.key-store=" + directory.resolve("missing.p12")));
  }

  private static CheetahServer startSecureServer() {
    return ApplicationRunner.run(SampleApp.class, "--server.port=0",
        "--server.ssl.enabled=true",
        "--server.ssl.key-store=" + keyStore,
        "--server.ssl.key-store-password=" + PASSWORD);
  }

  private static HttpClient trustingClient() throws Exception {
    KeyStore trusted = KeyStore.getInstance("PKCS12");
    try (InputStream input = Files.newInputStream(keyStore)) {
      trusted.load(input, PASSWORD.toCharArray());
    }

    TrustManagerFactory trustManagers =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trustManagers.init(trusted);

    SSLContext context = SSLContext.getInstance("TLS");
    context.init(null, trustManagers.getTrustManagers(), null);

    return HttpClient.newBuilder().sslContext(context).build();
  }

}
