package dev.subrosa.web;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Versioned AES-256-GCM envelope, bound to the key ID as authenticated data. */
final class PrivateKeyEncryption {
  private final SecretKeySpec secret;
  private final SecureRandom random = new SecureRandom();

  PrivateKeyEncryption(String encodedSecret) {
    if (encodedSecret == null || !encodedSecret.matches("[0-9a-fA-F]{64}")) {
      throw new IllegalArgumentException(
          "IDP_KEY_ENCRYPTION_SECRET must contain exactly 64 hexadecimal characters");
    }
    byte[] bytes = HexFormat.of().parseHex(encodedSecret);
    secret = new SecretKeySpec(bytes, "AES");
    Arrays.fill(bytes, (byte) 0);
  }

  String encrypt(String kid, byte[] plaintext) {
    byte[] nonce = new byte[12];
    random.nextBytes(nonce);
    try {
      var cipher = cipher(Cipher.ENCRYPT_MODE, kid, nonce);
      byte[] encrypted = cipher.doFinal(plaintext);
      return "v1:"
          + Base64.getEncoder()
              .encodeToString(
                  ByteBuffer.allocate(nonce.length + encrypted.length)
                      .put(nonce)
                      .put(encrypted)
                      .array());
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Unable to encrypt signing key");
    }
  }

  byte[] decrypt(String kid, String envelope) {
    try {
      if (!envelope.startsWith("v1:")) {
        throw new IllegalArgumentException();
      }
      byte[] bytes = Base64.getDecoder().decode(envelope.substring(3));
      if (bytes.length < 29) {
        throw new IllegalArgumentException();
      }
      return cipher(Cipher.DECRYPT_MODE, kid, Arrays.copyOf(bytes, 12))
          .doFinal(bytes, 12, bytes.length - 12);
    } catch (GeneralSecurityException | IllegalArgumentException exception) {
      throw new IllegalStateException(
          "Unable to decrypt signing key; check the encryption secret and stored data");
    }
  }

  private Cipher cipher(int mode, String kid, byte[] nonce) throws GeneralSecurityException {
    var cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(mode, secret, new GCMParameterSpec(128, nonce));
    cipher.updateAAD(("subrosa:signing-key:v1:" + kid).getBytes(StandardCharsets.UTF_8));
    return cipher;
  }
}
