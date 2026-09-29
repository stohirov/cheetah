package core.filter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import http.HttpMethod;
import http.HttpRequest;
import http.HttpResponse;
import http.HttpStatus;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

class CompressionFilterTest {

  private static final String LARGE_JSON = "{\"items\":\"" + "x".repeat(4096) + "\"}";

  private final CompressionFilter filter = new CompressionFilter(1024);

  @Test
  void compressesLargeTextResponses() throws Exception {
    HttpResponse original = HttpResponse.json(HttpStatus.OK, LARGE_JSON);

    HttpResponse response = filter.filter(request("gzip, deflate"), next -> original);

    assertEquals(Optional.of("gzip"), response.header("Content-Encoding"));
    assertEquals(Optional.of("Accept-Encoding"), response.header("Vary"));
    assertTrue(response.body().length < original.body().length);
    assertArrayEquals(original.body(), gunzip(response.body()));
  }

  @Test
  void keepsSmallResponsesUntouched() throws Exception {
    HttpResponse original = HttpResponse.json(HttpStatus.OK, "{}");

    assertSame(original, filter.filter(request("gzip"), next -> original));
  }

  @Test
  void respectsClientPreferences() throws Exception {
    HttpResponse original = HttpResponse.json(HttpStatus.OK, LARGE_JSON);

    HttpResponse identity = filter.filter(request("identity"), next -> original);
    HttpResponse refused = filter.filter(request("gzip;q=0, *;q=1"), next -> original);

    assertEquals(Optional.empty(), identity.header("Content-Encoding"));
    assertEquals(Optional.of("Accept-Encoding"), identity.header("Vary"));
    assertEquals(Optional.empty(), refused.header("Content-Encoding"));
  }

  @Test
  void skipsBinaryContent() throws Exception {
    HttpResponse image = new HttpResponse(HttpStatus.OK, Map.of("Content-Type", "image/png"),
        new byte[4096]);

    assertSame(image, filter.filter(request("gzip"), next -> image));
  }

  @Test
  void parsesAcceptEncoding() {
    assertTrue(CompressionFilter.acceptsGzip("br, GZIP;q=0.5"));
    assertTrue(CompressionFilter.acceptsGzip("*"));
    assertFalse(CompressionFilter.acceptsGzip(""));
    assertFalse(CompressionFilter.acceptsGzip("gzip;q=0"));
  }

  private static HttpRequest request(String acceptEncoding) {
    return new HttpRequest(HttpMethod.GET, "/", "HTTP/1.1", Map.of(),
        Map.of("accept-encoding", acceptEncoding), new byte[0]);
  }

  private static byte[] gunzip(byte[] compressed) throws IOException {
    try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
      return input.readAllBytes();
    }
  }

}
