package dev.subrosa.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;

/**
 * Executes issuance under a database transaction and lock. Callback failures roll back consumption.
 * Replays commit family revocation and return empty; expired/unknown grants return empty. All
 * refresh descendants share an absolute expiry. Callbacks must not perform external I/O.
 */
public interface OAuthGrantStore {
  /** Stores a new hashed authorization code and initializes its refresh family. */
  void saveCode(AuthorizationCode code);

  /** Stores a hashed descendant within the current issuance transaction. */
  void saveRefresh(RefreshToken token);

  /**
   * Consumes a valid code and issues credentials atomically, committing family revocation on
   * replay.
   */
  <T> Optional<T> redeemCode(String hash, Instant now, Function<AuthorizationCode, T> issue);

  /** Rotates a valid refresh credential atomically, committing family revocation on reuse. */
  <T> Optional<T> rotateRefresh(String hash, Instant now, Function<RefreshToken, T> issue);
}
