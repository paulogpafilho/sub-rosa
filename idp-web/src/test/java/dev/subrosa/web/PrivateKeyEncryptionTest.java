package dev.subrosa.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class PrivateKeyEncryptionTest {
  @Test
  void authenticatesCiphertextAndKeyIdWithFreshNonces() {
    var encryption = new PrivateKeyEncryption("a1".repeat(32));
    byte[] plaintext = "private-key-bytes".getBytes(StandardCharsets.UTF_8);
    String encrypted = encryption.encrypt("key-1", plaintext);
    assertThat(encryption.decrypt("key-1", encrypted)).isEqualTo(plaintext);
    assertThat(encryption.encrypt("key-1", plaintext)).isNotEqualTo(encrypted);
    assertThat(encrypted).doesNotContain("private-key-bytes");
    assertThatThrownBy(() -> encryption.decrypt("key-2", encrypted))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> new PrivateKeyEncryption("b2".repeat(32)).decrypt("key-1", encrypted))
        .isInstanceOf(IllegalStateException.class);
    byte[] corrupt = Base64.getDecoder().decode(encrypted.substring(3));
    corrupt[corrupt.length - 1] ^= 1;
    assertThatThrownBy(
            () -> encryption.decrypt("key-1", "v1:" + Base64.getEncoder().encodeToString(corrupt)))
        .isInstanceOf(IllegalStateException.class);
    for (String invalid : new String[] {"v2:abc", "v1:!", "v1:AA==", ""}) {
      assertThatThrownBy(() -> encryption.decrypt("key-1", invalid))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Test
  void secretIsRequiredAndStrictlyHexEncoded() {
    for (String invalid : new String[] {null, "", "a".repeat(63), "a".repeat(65), "g".repeat(64)}) {
      assertThatThrownBy(() -> new PrivateKeyEncryption(invalid))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("IDP_KEY_ENCRYPTION_SECRET");
    }
  }
}
