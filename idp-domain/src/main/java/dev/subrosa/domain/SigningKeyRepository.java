package dev.subrosa.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Stores encrypted signing-key history and atomic lifecycle transitions. */
public interface SigningKeyRepository {
  /** Returns the active signing key, if one exists. */
  Optional<SigningKey> active();

  /** Returns key snapshots still published for verification at the given time. */
  List<SigningKey> publishedAt(Instant now);

  /** Serialize first-start generation; invoke the factory only if no key exists. */
  SigningKey initialize(Supplier<SigningKey> factory);

  /** Atomically retire the active key and insert its replacement. */
  SigningKey rotate(Supplier<SigningKey> factory, Instant now, Instant retireAt);
}
