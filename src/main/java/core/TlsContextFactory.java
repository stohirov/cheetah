package core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import models.TlsSettings;

public final class TlsContextFactory {

  private static final String CLASSPATH_PREFIX = "classpath:";
  private static final List<String> PROTOCOLS = List.of("TLSv1.3", "TLSv1.2");

  private TlsContextFactory() {
  }

  public static SSLContext create(TlsSettings settings, ClassLoader classLoader)
      throws IOException {
    char[] storePassword = password(settings.keyStorePassword());
    char[] keyPassword = settings.keyPassword() == null
        ? storePassword
        : password(settings.keyPassword());

    try (InputStream input = open(settings.keyStore(), classLoader)) {
      KeyStore keyStore = KeyStore.getInstance(settings.keyStoreType());
      keyStore.load(input, storePassword);

      KeyManagerFactory keyManagers =
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      keyManagers.init(keyStore, keyPassword);

      SSLContext context = SSLContext.getInstance("TLS");
      context.init(keyManagers.getKeyManagers(), null, null);

      return context;
    } catch (GeneralSecurityException e) {
      throw new IOException("Cannot load key store " + settings.keyStore(), e);
    }
  }

  public static void restrictProtocols(SSLServerSocket socket) {
    String[] enabled = Arrays.stream(socket.getSupportedProtocols())
        .filter(PROTOCOLS::contains)
        .toArray(String[]::new);

    socket.setEnabledProtocols(enabled);
  }

  private static InputStream open(String location, ClassLoader classLoader) throws IOException {
    if (!location.startsWith(CLASSPATH_PREFIX)) {
      return Files.newInputStream(Path.of(location));
    }

    String resource = location.substring(CLASSPATH_PREFIX.length());
    InputStream input = classLoader.getResourceAsStream(resource);
    if (input == null) {
      throw new IOException("Key store not found on classpath: " + resource);
    }

    return input;
  }

  private static char[] password(String value) {
    return value == null ? new char[0] : value.toCharArray();
  }

}
