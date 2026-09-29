package http;

public enum HttpStatus {
  OK(200, "OK"),
  CREATED(201, "Created"),
  ACCEPTED(202, "Accepted"),
  NO_CONTENT(204, "No Content"),
  MOVED_PERMANENTLY(301, "Moved Permanently"),
  FOUND(302, "Found"),
  NOT_MODIFIED(304, "Not Modified"),
  BAD_REQUEST(400, "Bad Request"),
  UNAUTHORIZED(401, "Unauthorized"),
  FORBIDDEN(403, "Forbidden"),
  NOT_FOUND(404, "Not Found"),
  METHOD_NOT_ALLOWED(405, "Method Not Allowed"),
  CONFLICT(409, "Conflict"),
  CONTENT_TOO_LARGE(413, "Content Too Large"),
  UNSUPPORTED_MEDIA_TYPE(415, "Unsupported Media Type"),
  UNPROCESSABLE_CONTENT(422, "Unprocessable Content"),
  INTERNAL_SERVER_ERROR(500, "Internal Server Error"),
  NOT_IMPLEMENTED(501, "Not Implemented"),
  SERVICE_UNAVAILABLE(503, "Service Unavailable"),
  HTTP_VERSION_NOT_SUPPORTED(505, "HTTP Version Not Supported");

  private final int code;
  private final String reason;

  HttpStatus(int code, String reason) {
    this.code = code;
    this.reason = reason;
  }

  public int code() {
    return code;
  }

  public String reason() {
    return reason;
  }
}
