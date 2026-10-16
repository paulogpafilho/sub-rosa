package dev.subrosa.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
	Optional<User> findByUsername(String username);

	boolean hasUsers();

	/**
	 * Serialized with other bootstrap operations; inserts only into an empty user
	 * table.
	 */
	boolean createFirst(User user);

	/** Atomic failure increment, resetting an expired lock's counter. */
	boolean recordFailure(UUID id, Instant now, int threshold, Duration lockDuration);

	/**
	 * Succeeds only if enabled, unlocked and unchanged since password verification.
	 */
	boolean recordSuccess(User verified, Instant now);
}
