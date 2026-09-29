package core;

import core.error.ErrorBody;
import core.handler.Handler;
import http.HttpException;
import http.HttpMethod;
import http.HttpRequest;
import http.HttpRequestParser;
import http.HttpResponse;
import http.HttpResponseWriter;
import http.HttpStatus;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLServerSocket;
import models.Server;
import models.TlsSettings;

public class CheetahServer implements AutoCloseable {

  private static final Logger LOGGER = Logger.getLogger(CheetahServer.class.getName());
  private static final int IDLE_TIMEOUT_MILLIS = 30_000;

  private final Server config;
  private final Handler handler;
  private final ClassLoader classLoader;
  private final HttpRequestParser parser = new HttpRequestParser();
  private final HttpResponseWriter writer = new HttpResponseWriter();
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  private ServerSocket serverSocket;

  public CheetahServer(Server config, Handler handler) {
    this(config, handler, Thread.currentThread().getContextClassLoader());
  }

  public CheetahServer(Server config, Handler handler, ClassLoader classLoader) {
    this.config = config;
    this.handler = handler;
    this.classLoader = classLoader;
  }

  public synchronized void start() throws IOException {
    if (serverSocket != null) {
      throw new IllegalStateException("Server is already started");
    }

    serverSocket = createServerSocket();
    serverSocket.setReuseAddress(true);
    serverSocket.bind(new InetSocketAddress(config.getPort()));

    Thread.ofPlatform().name("cheetah-acceptor").start(this::acceptConnections);

    LOGGER.info("Cheetah started on port " + port() + " (" + scheme() + ")");
  }

  public boolean isSecure() {
    return config.getTls().isPresent();
  }

  private String scheme() {
    return isSecure() ? "https" : "http";
  }

  private ServerSocket createServerSocket() throws IOException {
    Optional<TlsSettings> tls = config.getTls();
    if (tls.isEmpty()) {
      return new ServerSocket();
    }

    SSLServerSocket socket = (SSLServerSocket) TlsContextFactory.create(tls.get(), classLoader)
        .getServerSocketFactory()
        .createServerSocket();
    TlsContextFactory.restrictProtocols(socket);

    return socket;
  }

  public int port() {
    return serverSocket.getLocalPort();
  }

  @Override
  public synchronized void close() throws IOException {
    if (serverSocket == null || serverSocket.isClosed()) {
      return;
    }

    serverSocket.close();
    executor.shutdownNow();

    try {
      executor.awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    LOGGER.info("Cheetah stopped");
  }

  private void acceptConnections() {
    while (!serverSocket.isClosed()) {
      try {
        Socket socket = serverSocket.accept();
        executor.submit(() -> serve(socket));
      } catch (IOException e) {
        if (!serverSocket.isClosed()) {
          LOGGER.log(Level.WARNING, "Failed to accept connection", e);
        }
      }
    }
  }

  private void serve(Socket socket) {
    try (socket) {
      socket.setSoTimeout(IDLE_TIMEOUT_MILLIS);

      InputStream input = new BufferedInputStream(socket.getInputStream());
      OutputStream output = new BufferedOutputStream(socket.getOutputStream());

      boolean keepAlive = true;
      while (keepAlive) {
        Optional<HttpRequest> request;
        try {
          request = parser.parse(input);
        } catch (HttpException e) {
          writer.write(output, ErrorBody.response(e, null), false);
          return;
        }

        if (request.isEmpty()) {
          return;
        }

        HttpRequest current = request.get().withSecure(isSecure());
        keepAlive = current.keepAlive();
        boolean includeBody = current.method() != HttpMethod.HEAD;

        writer.write(output, dispatch(current), keepAlive, includeBody);
      }
    } catch (IOException e) {
      LOGGER.log(Level.FINE, "Connection closed", e);
    }
  }

  private HttpResponse dispatch(HttpRequest request) {
    try {
      return handler.handle(request);
    } catch (HttpException e) {
      return ErrorBody.response(e, request.path());
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, "Unhandled error for " + request.method() + " " + request.path(), e);

      return ErrorBody.response(HttpStatus.INTERNAL_SERVER_ERROR, null, request.path());
    }
  }

}
