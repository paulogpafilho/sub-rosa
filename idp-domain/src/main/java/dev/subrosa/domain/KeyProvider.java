package dev.subrosa.domain;

import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;

/** Supplies local signing credentials and published verification keys. */
public interface KeyProvider {
  /** Returns the active credentials used to sign new tokens. */
  SigningCredentials activeSigningKey();

  /** Returns active and still-published retiring public keys. */
  List<PublicKey> verificationKeys();

  /** Public verification material that is safe to publish. */
  record PublicKey(String kid, RSAPublicKey key, String certificatePem) {}

  /** Active signing material whose private key must never be logged. */
  record SigningCredentials(String kid, RSAPublicKey publicKey, PrivateKey privateKey) {
    @Override
    public String toString() {
      return "SigningCredentials[kid=" + kid + "]";
    }
  }
}
