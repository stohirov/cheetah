package core.filter;

import annotations.Order;
import http.HttpRequest;
import http.HttpResponse;
import http.HttpStatus;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

@Order(Integer.MIN_VALUE + 1)
public class CompressionFilter implements Filter {

  private static final String GZIP = "gzip";
  private static final String ACCEPT_ENCODING = "Accept-Encoding";
  private static final Set<String> COMPRESSIBLE_TYPES = Set.of(
      "application/json",
      "application/javascript",
      "application/xml",
      "image/svg+xml");

  private final int minimumSize;

  public CompressionFilter(int minimumSize) {
    this.minimumSize = minimumSize;
  }

  @Override
  public HttpResponse filter(HttpRequest request, FilterChain chain) throws Exception {
    HttpResponse response = chain.next(request);

    if (!isCompressible(response)) {
      return response;
    }

    HttpResponse varied = response.withHeader("Vary", vary(response));
    if (!acceptsGzip(request.header(ACCEPT_ENCODING).orElse(""))) {
      return varied;
    }

    byte[] compressed = gzip(response.body());
    if (compressed.length >= response.body().length) {
      return varied;
    }

    return varied.withBody(compressed).withHeader("Content-Encoding", GZIP);
  }

  private boolean isCompressible(HttpResponse response) {
    HttpStatus status = response.status();
    if (status == HttpStatus.NO_CONTENT || status == HttpStatus.NOT_MODIFIED) {
      return false;
    }

    if (response.header("Content-Encoding").isPresent() || response.body().length < minimumSize) {
      return false;
    }

    String mediaType = response.header("Content-Type").orElse("").split(";")[0].trim()
        .toLowerCase(Locale.ROOT);

    return mediaType.startsWith("text/")
        || mediaType.endsWith("+json")
        || mediaType.endsWith("+xml")
        || COMPRESSIBLE_TYPES.contains(mediaType);
  }

  static boolean acceptsGzip(String acceptEncoding) {
    Double gzipQuality = null;
    Double wildcardQuality = null;

    for (String token : acceptEncoding.split(",")) {
      String[] parts = token.trim().split(";");
      String coding = parts[0].trim().toLowerCase(Locale.ROOT);
      double quality = quality(parts);

      if (coding.equals(GZIP)) {
        gzipQuality = quality;
      } else if (coding.equals("*")) {
        wildcardQuality = quality;
      }
    }

    Double effective = gzipQuality != null ? gzipQuality : wildcardQuality;

    return effective != null && effective > 0;
  }

  private static double quality(String[] parts) {
    for (int i = 1; i < parts.length; i++) {
      String parameter = parts[i].trim();
      if (parameter.startsWith("q=")) {
        try {
          return Double.parseDouble(parameter.substring(2));
        } catch (NumberFormatException e) {
          return 0;
        }
      }
    }

    return 1;
  }

  private static String vary(HttpResponse response) {
    String existing = response.header("Vary").orElse("");
    if (existing.isBlank()) {
      return ACCEPT_ENCODING;
    }

    boolean present = existing.toLowerCase(Locale.ROOT).contains("accept-encoding");

    return present ? existing : existing + ", " + ACCEPT_ENCODING;
  }

  private static byte[] gzip(byte[] body) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream(body.length / 2);

    try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
      gzip.write(body);
    }

    return output.toByteArray();
  }

}
