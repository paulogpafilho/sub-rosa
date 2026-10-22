package dev.subrosa.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BcryptPasswordHasherTest {
  private final BcryptPasswordHasher passwords = new BcryptPasswordHasher(10, 72);

  @Test
  void usesSaltAndChecksPasswordsWithoutTruncation() {
    String password = "é".repeat(36);
    String hash = passwords.hash(password);
    assertThat(hash).startsWith("$2a$10$");
    assertThat(passwords.hash(password)).isNotEqualTo(hash);
    assertThat(passwords.matches(password, hash)).isTrue();
    assertThat(passwords.matches(password + "a", hash)).isFalse();
    assertThat(passwords.matches("wrong", hash)).isFalse();
    assertThat(passwords.matches(null, hash)).isFalse();
    assertThat(passwords.matches("", hash)).isFalse();
    assertThatThrownBy(() -> passwords.hash("é".repeat(37)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> passwords.hash("a".repeat(73)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void honorsLowerConfiguredByteLimit() {
    var restricted = new BcryptPasswordHasher(10, 20);
    assertThatThrownBy(() -> restricted.hash("é".repeat(11)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(restricted.matches("é".repeat(11), passwords.hash("test"))).isFalse();
  }
}
