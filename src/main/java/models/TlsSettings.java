package models;

public record TlsSettings(
    String keyStore,
    String keyStorePassword,
    String keyStoreType,
    String keyPassword) {

  public TlsSettings {
    if (keyStore == null || keyStore.isBlank()) {
      throw new IllegalArgumentException("A key store is required when TLS is enabled");
    }
  }
}
