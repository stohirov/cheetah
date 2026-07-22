package models;

public class Server {
  private final int port;

  public Server(int port) {
    this.port = port;
  }

  public int getPort() {
    return port;
  }
}
