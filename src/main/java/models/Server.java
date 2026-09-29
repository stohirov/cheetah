package models;

import java.util.Optional;

public class Server {
  private final int port;
  private final TlsSettings tls;

  public Server(int port) {
    this(port, null);
  }

  public Server(int port, TlsSettings tls) {
    this.port = port;
    this.tls = tls;
  }

  public int getPort() {
    return port;
  }

  public Optional<TlsSettings> getTls() {
    return Optional.ofNullable(tls);
  }
}
