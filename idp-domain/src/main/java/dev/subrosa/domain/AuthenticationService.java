package dev.subrosa.domain;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/** Authenticates identities with generic failures and atomic lockout transitions. */
public final class AuthenticationService {
  private final UserRepository users;
  private final PasswordHasher passwords;
  private final Clock clock;
  private final int threshold;
  private final Duration lockDuration;

  /** Uses the supplied password, identity and clock ports with the configured lockout policy. */
  public AuthenticationService(
      UserRepository users,
      PasswordHasher passwords,
      Clock clock,
      int threshold,
      Duration lockDuration) {
    if (threshold < 1 || lockDuration.isNegative() || lockDuration.isZero()) {
      throw new IllegalArgumentException("Invalid lockout policy");
    }
    this.users = users;
    this.passwords = passwords;
    this.clock = clock;
    this.threshold = threshold;
    this.lockDuration = lockDuration;
  }

  /**
   * Verifies credentials and atomically records success or failure without revealing account
   * existence.
   */
  public Optional<User> authenticate(String username, String password) {
    var found =
        username == null || username.length() > 200
            ? Optional.<User>empty()
            : users.findByUsername(username);
    if (found.isEmpty()) {
      passwords.dummyMatch(password);
      return Optional.empty();
    }
    var user = found.orElseThrow();
    boolean matches = passwords.matches(password, user.passwordHash());
    var now = clock.instant();
    if (!user.canAuthenticate(now)) {
      return Optional.empty();
    }
    if (!matches) {
      users.recordFailure(user.id(), now, threshold, lockDuration);
      return Optional.empty();
    }
    return users.recordSuccess(user, now) ? Optional.of(user) : Optional.empty();
  }
}
