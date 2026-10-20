package dev.subrosa.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public interface SigningKeyRepository {
	Optional<SigningKey> active();
	List<SigningKey> publishedAt(Instant now);
	/**
	 * Serialize first-start generation; invoke the factory only if no key exists.
	 */
	SigningKey initialize(Supplier<SigningKey> factory);
	/** Atomically retire the active key and insert its replacement. */
	SigningKey rotate(Supplier<SigningKey> factory, Instant now, Instant retireAt);
}
