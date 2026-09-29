package http;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class HttpRequestParser {

  private static final int MAX_LINE_LENGTH = 8 * 1024;
  private static final int MAX_HEADER_COUNT = 100;
  private static final long MAX_BODY_LENGTH = 10L * 1024 * 1024;

  public Optional<HttpRequest> parse(InputStream input) throws IOException {
    String requestLine = readLine(input);
    if (requestLine == null) {
      return Optional.empty();
    }

    String[] parts = requestLine.split(" ", -1);
    if (parts.length != 3) {
      throw new HttpException(HttpStatus.BAD_REQUEST, "Malformed request line");
    }

    HttpMethod method = parseMethod(parts[0]);
    URI target = parseTarget(parts[1]);
    String version = parseVersion(parts[2]);

    Map<String, String> headers = readHeaders(input);
    byte[] body = readBody(input, headers);

    return Optional.of(new HttpRequest(method, target.getPath(), version,
        parseQuery(target.getRawQuery()), headers, body));
  }

  private static HttpMethod parseMethod(String value) {
    try {
      return HttpMethod.valueOf(value);
    } catch (IllegalArgumentException e) {
      throw new HttpException(HttpStatus.NOT_IMPLEMENTED, "Unsupported method: " + value, e);
    }
  }

  private static URI parseTarget(String value) {
    if (!value.startsWith("/")) {
      throw new HttpException(HttpStatus.BAD_REQUEST, "Request target must be an absolute path");
    }

    try {
      return new URI(value);
    } catch (URISyntaxException e) {
      throw new HttpException(HttpStatus.BAD_REQUEST, "Malformed request target", e);
    }
  }

  private static String parseVersion(String value) {
    if (!value.equals("HTTP/1.1") && !value.equals("HTTP/1.0")) {
      throw new HttpException(HttpStatus.HTTP_VERSION_NOT_SUPPORTED, "Unsupported: " + value);
    }

    return value;
  }

  private static Map<String, List<String>> parseQuery(String rawQuery) {
    if (rawQuery == null || rawQuery.isEmpty()) {
      return Map.of();
    }

    Map<String, List<String>> params = new LinkedHashMap<>();
    for (String pair : rawQuery.split("&")) {
      if (pair.isEmpty()) {
        continue;
      }

      int separator = pair.indexOf('=');
      String name = separator < 0 ? pair : pair.substring(0, separator);
      String value = separator < 0 ? "" : pair.substring(separator + 1);

      params.computeIfAbsent(decode(name), key -> new ArrayList<>()).add(decode(value));
    }

    Map<String, List<String>> result = new LinkedHashMap<>();
    params.forEach((name, values) -> result.put(name, List.copyOf(values)));

    return result;
  }

  private static String decode(String value) {
    try {
      return URLDecoder.decode(value, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      throw new HttpException(HttpStatus.BAD_REQUEST, "Malformed query string", e);
    }
  }

  private static Map<String, String> readHeaders(InputStream input) throws IOException {
    Map<String, String> headers = new HashMap<>();

    while (true) {
      String line = readLine(input);
      if (line == null) {
        throw new EOFException("Connection closed while reading headers");
      }

      if (line.isEmpty()) {
        return headers;
      }

      if (headers.size() >= MAX_HEADER_COUNT) {
        throw new HttpException(HttpStatus.BAD_REQUEST, "Too many headers");
      }

      int separator = line.indexOf(':');
      if (separator <= 0) {
        throw new HttpException(HttpStatus.BAD_REQUEST, "Malformed header: " + line);
      }

      String name = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
      String value = line.substring(separator + 1).trim();

      headers.merge(name, value, (existing, added) -> existing + ", " + added);
    }
  }

  private static byte[] readBody(InputStream input, Map<String, String> headers)
      throws IOException {
    String transferEncoding = headers.get("transfer-encoding");
    if (transferEncoding != null) {
      if (headers.containsKey("content-length")) {
        throw new HttpException(HttpStatus.BAD_REQUEST,
            "Transfer-Encoding and Content-Length must not both be present");
      }

      if (!transferEncoding.trim().equalsIgnoreCase("chunked")) {
        throw new HttpException(HttpStatus.NOT_IMPLEMENTED,
            "Unsupported Transfer-Encoding: " + transferEncoding);
      }

      return readChunkedBody(input);
    }

    String contentLength = headers.get("content-length");
    if (contentLength == null) {
      return new byte[0];
    }

    long length;
    try {
      length = Long.parseLong(contentLength);
    } catch (NumberFormatException e) {
      throw new HttpException(HttpStatus.BAD_REQUEST, "Invalid Content-Length", e);
    }

    if (length < 0) {
      throw new HttpException(HttpStatus.BAD_REQUEST, "Invalid Content-Length");
    }

    if (length > MAX_BODY_LENGTH) {
      throw new HttpException(HttpStatus.CONTENT_TOO_LARGE, "Request body is too large");
    }

    byte[] body = input.readNBytes((int) length);
    if (body.length != length) {
      throw new EOFException("Connection closed while reading body");
    }

    return body;
  }

  private static byte[] readChunkedBody(InputStream input) throws IOException {
    ByteArrayOutputStream body = new ByteArrayOutputStream();

    while (true) {
      long size = parseChunkSize(requireLine(input));
      if (size == 0) {
        break;
      }

      if (body.size() + size > MAX_BODY_LENGTH) {
        throw new HttpException(HttpStatus.CONTENT_TOO_LARGE, "Request body is too large");
      }

      byte[] chunk = input.readNBytes((int) size);
      if (chunk.length != size) {
        throw new EOFException("Connection closed while reading chunk");
      }

      body.writeBytes(chunk);

      if (!requireLine(input).isEmpty()) {
        throw new HttpException(HttpStatus.BAD_REQUEST, "Malformed chunk terminator");
      }
    }

    String trailer;
    do {
      trailer = requireLine(input);
    } while (!trailer.isEmpty());

    return body.toByteArray();
  }

  private static long parseChunkSize(String line) {
    int extension = line.indexOf(';');
    String hex = (extension < 0 ? line : line.substring(0, extension)).trim();

    try {
      long size = Long.parseLong(hex, 16);
      if (size < 0 || hex.startsWith("+")) {
        throw new NumberFormatException(hex);
      }

      return size;
    } catch (NumberFormatException e) {
      throw new HttpException(HttpStatus.BAD_REQUEST, "Invalid chunk size: " + line, e);
    }
  }

  private static String requireLine(InputStream input) throws IOException {
    String line = readLine(input);
    if (line == null) {
      throw new EOFException("Connection closed while reading chunked body");
    }

    return line;
  }

  private static String readLine(InputStream input) throws IOException {
    ByteArrayOutputStream line = new ByteArrayOutputStream();

    int current;
    while ((current = input.read()) != -1) {
      if (current == '\n') {
        return stripCarriageReturn(line.toString(StandardCharsets.ISO_8859_1));
      }

      if (line.size() >= MAX_LINE_LENGTH) {
        throw new HttpException(HttpStatus.BAD_REQUEST, "Line is too long");
      }

      line.write(current);
    }

    if (line.size() == 0) {
      return null;
    }

    throw new EOFException("Connection closed mid-line");
  }

  private static String stripCarriageReturn(String line) {
    return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
  }

}
