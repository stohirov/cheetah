package http;

import java.time.Duration;
import java.util.regex.Pattern;

public record Cookie(
    String name,
    String value,
    String path,
    String domain,
    Duration maxAge,
    boolean secure,
    boolean httpOnly,
    SameSite sameSite) {

  private static final Pattern NAME = Pattern.compile("[!#$%&'*+\\-.^_`|~0-9A-Za-z]+");
  private static final Pattern VALUE =
      Pattern.compile("[\\x21\\x23-\\x2B\\x2D-\\x3A\\x3C-\\x5B\\x5D-\\x7E]*");
  private static final Pattern ATTRIBUTE = Pattern.compile("[\\x20-\\x3A\\x3C-\\x7E]*");

  public enum SameSite {
    STRICT("Strict"),
    LAX("Lax"),
    NONE("None");

    private final String attribute;

    SameSite(String attribute) {
      this.attribute = attribute;
    }

    public String attribute() {
      return attribute;
    }
  }

  public Cookie {
    if (name == null || !NAME.matcher(name).matches()) {
      throw new IllegalArgumentException("Invalid cookie name: " + name);
    }

    if (value == null || !VALUE.matcher(value).matches()) {
      throw new IllegalArgumentException("Invalid value for cookie " + name);
    }

    requireAttribute(path, "path");
    requireAttribute(domain, "domain");

    if (maxAge != null && maxAge.isNegative()) {
      throw new IllegalArgumentException("Cookie max-age must not be negative");
    }
  }

  public static Cookie of(String name, String value) {
    return new Cookie(name, value, "/", null, null, false, false, null);
  }

  public static Cookie expired(String name) {
    return of(name, "").withMaxAge(Duration.ZERO);
  }

  public Cookie withPath(String newPath) {
    return new Cookie(name, value, newPath, domain, maxAge, secure, httpOnly, sameSite);
  }

  public Cookie withDomain(String newDomain) {
    return new Cookie(name, value, path, newDomain, maxAge, secure, httpOnly, sameSite);
  }

  public Cookie withMaxAge(Duration newMaxAge) {
    return new Cookie(name, value, path, domain, newMaxAge, secure, httpOnly, sameSite);
  }

  public Cookie withSecure(boolean newSecure) {
    return new Cookie(name, value, path, domain, maxAge, newSecure, httpOnly, sameSite);
  }

  public Cookie withHttpOnly(boolean newHttpOnly) {
    return new Cookie(name, value, path, domain, maxAge, secure, newHttpOnly, sameSite);
  }

  public Cookie withSameSite(SameSite newSameSite) {
    return new Cookie(name, value, path, domain, maxAge, secure, httpOnly, newSameSite);
  }

  public String toHeader() {
    StringBuilder header = new StringBuilder(name).append('=').append(value);

    if (path != null) {
      header.append("; Path=").append(path);
    }

    if (domain != null) {
      header.append("; Domain=").append(domain);
    }

    if (maxAge != null) {
      header.append("; Max-Age=").append(maxAge.toSeconds());
    }

    if (secure) {
      header.append("; Secure");
    }

    if (httpOnly) {
      header.append("; HttpOnly");
    }

    if (sameSite != null) {
      header.append("; SameSite=").append(sameSite.attribute());
    }

    return header.toString();
  }

  private static void requireAttribute(String attribute, String label) {
    if (attribute != null && !ATTRIBUTE.matcher(attribute).matches()) {
      throw new IllegalArgumentException("Invalid cookie " + label + ": " + attribute);
    }
  }

}
