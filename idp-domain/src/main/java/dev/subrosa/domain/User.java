package dev.subrosa.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Identity snapshot. Password hashes must never be rendered or logged. */
public record User(UUID id, String username, String email, String passwordHash, boolean enabled, int failedAttempts,
		Instant lockedUntil, Instant createdAt) {
	public User {
		Objects.requireNonNull(id);
		Objects.requireNonNull(createdAt);
		if (username == null || username.isBlank() || username.length() > 200 || passwordHash == null
				|| passwordHash.isBlank() || failedAttempts < 0) {
			throw new IllegalArgumentException("Invalid user");
		}
	}

	public boolean canAuthenticate(Instant now) {
		return enabled && (lockedUntil == null || !lockedUntil.isAfter(now));
	}

	@Override
	public String toString() {
		return "User[id=" + id + "]";
	}
}
