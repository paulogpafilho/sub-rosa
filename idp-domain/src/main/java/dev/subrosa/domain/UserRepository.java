package dev.subrosa.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Loads identity snapshots and applies atomic authentication transitions. */
public interface UserRepository {
  /** Looks up a stored aggregate by its stable identifier. */
  Optional<User> findById(UUID id);

  /** Looks up an identity using its login name. */
  Optional<User> findByUsername(String username);

  /** Reports whether bootstrap must preserve existing identities. */
  boolean hasUsers();

  /** Serialized with other bootstrap operations; inserts only into an empty user table. */
  boolean createFirst(User user);

  /** Atomic failure increment, resetting an expired lock's counter. */
  boolean recordFailure(UUID id, Instant now, int threshold, Duration lockDuration);

  /** Succeeds only if enabled, unlocked and unchanged since password verification. */
  boolean recordSuccess(User verified, Instant now);
}
