package dev.subrosa.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthenticationServiceTest {
  @Test
  void unknownIdentityPerformsDummyPasswordWorkWithoutAccountMutation() {
    var passwords = new RecordingPasswords();
    var service =
        new AuthenticationService(
            new UnknownUsers(),
            passwords,
            Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
            5,
            Duration.ofMinutes(15));
    assertTrue(service.authenticate("missing", "secret").isEmpty());
    assertTrue(service.authenticate("a".repeat(201), "secret").isEmpty());
    assertEquals(2, passwords.dummyChecks);
  }

  private static final class RecordingPasswords implements PasswordHasher {
    int dummyChecks;

    @Override
    public String hash(String password) {
      throw new AssertionError("No hash creation during authentication");
    }

    @Override
    public boolean matches(String password, String hash) {
      throw new AssertionError("Unknown account has no real hash");
    }

    @Override
    public void dummyMatch(String password) {
      dummyChecks++;
    }
  }

  private static final class UnknownUsers implements UserRepository {
    @Override
    public Optional<User> findById(UUID id) {
      return Optional.empty();
    }

    @Override
    public Optional<User> findByUsername(String username) {
      return Optional.empty();
    }

    @Override
    public boolean hasUsers() {
      throw new AssertionError("No bootstrap during authentication");
    }

    @Override
    public boolean createFirst(User user) {
      throw new AssertionError("No bootstrap during authentication");
    }

    @Override
    public boolean recordFailure(UUID id, Instant now, int threshold, Duration duration) {
      throw new AssertionError("Unknown account cannot be mutated");
    }

    @Override
    public boolean recordSuccess(User user, Instant now) {
      throw new AssertionError("Unknown account cannot authenticate");
    }
  }
}
