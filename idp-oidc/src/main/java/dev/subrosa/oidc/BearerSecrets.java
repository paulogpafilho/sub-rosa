package dev.subrosa.oidc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Generates high-entropy credentials and implements SHA-256 and S256 primitives. */
public final class BearerSecrets {
  private final SecureRandom random;

  /** Uses the supplied cryptographically secure random source for credential generation. */
  public BearerSecrets(SecureRandom random) {
    this.random = random;
  }

  /** Returns a URL-safe credential containing 256 random bits. */
  public String generate() {
    var bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** Returns the lowercase hexadecimal SHA-256 digest of a credential. */
  public static String hash(String value) {
    return HexFormat.of().formatHex(digest(value));
  }

  /** Compares non-null encoded digests in constant time. */
  public static boolean matches(String expected, String actual) {
    return expected != null
        && actual != null
        && MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.US_ASCII),
            actual.getBytes(StandardCharsets.US_ASCII));
  }

  /** Validates a PKCE verifier and computes its S256 challenge. */
  public static String challenge(String verifier) {
    if (verifier == null || !verifier.matches("[A-Za-z0-9._~-]{43,128}")) {
      throw new OAuthError("invalid_grant");
    }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest(verifier));
  }

  private static byte[] digest(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
