package dev.subrosa.persistence;

import dev.subrosa.domain.AuthorizationCode;
import dev.subrosa.domain.OAuthGrantStore;
import dev.subrosa.domain.RefreshToken;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Serializes grant consumption, issuance and family revocation in database transactions. */
@Repository
public class OAuthGrantStoreAdapter implements OAuthGrantStore {
  private final JpaAuthorizationCodeRepository codes;
  private final JpaRefreshTokenRepository refresh;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transaction;

  /** Uses JPA conditional updates and repeatable-read transactions for issuance callbacks. */
  public OAuthGrantStoreAdapter(
      JpaAuthorizationCodeRepository codes,
      JpaRefreshTokenRepository refresh,
      JdbcTemplate jdbc,
      PlatformTransactionManager transactions) {
    this.codes = codes;
    this.refresh = refresh;
    this.jdbc = jdbc;
    this.transaction = new TransactionTemplate(transactions);
    this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
  }

  @Override
  @Transactional
  public void saveCode(AuthorizationCode code) {
    var e = new AuthorizationCodeEntity();
    e.hash = code.hash();
    e.familyId = code.familyId();
    e.clientId = code.clientId();
    e.userId = code.userId();
    e.redirectUri = code.redirectUri();
    e.scopes = join(code.scopes());
    e.nonce = code.nonce();
    e.challenge = code.challenge();
    e.authTime = code.authTime();
    e.expiresAt = code.expiresAt();
    codes.saveAndFlush(e);
    jdbc.update("INSERT INTO refresh_families(family_id) VALUES (?)", code.familyId());
  }

  @Override
  @Transactional
  public void saveRefresh(RefreshToken token) {
    var e = new RefreshTokenEntity();
    e.hash = token.hash();
    e.familyId = token.familyId();
    e.clientId = token.clientId();
    e.userId = token.userId();
    e.scopes = join(token.scopes());
    e.nonce = token.nonce();
    e.authTime = token.authTime();
    e.expiresAt = token.expiresAt();
    refresh.saveAndFlush(e);
  }

  @Override
  public <T> Optional<T> redeemCode(
      String hash, Instant now, Function<AuthorizationCode, T> issue) {
    return transact(() -> consumeCode(hash, now, issue));
  }

  private <T> Optional<T> consumeCode(
      String hash, Instant now, Function<AuthorizationCode, T> issue) {
    var found = codes.locked(hash);
    if (found.isEmpty()) {
      return Optional.empty();
    }
    var e = found.get();
    if (e.consumedAt != null) {
      revoke(e.familyId, now);
      return Optional.empty();
    }
    if (!e.expiresAt.isAfter(now) || codes.consume(hash, now) != 1) {
      return Optional.empty();
    }
    return Optional.of(
        issue.apply(
            new AuthorizationCode(
                e.hash,
                e.familyId,
                e.clientId,
                e.userId,
                e.redirectUri,
                split(e.scopes),
                e.nonce,
                e.challenge,
                e.authTime,
                e.expiresAt)));
  }

  @Override
  public <T> Optional<T> rotateRefresh(String hash, Instant now, Function<RefreshToken, T> issue) {
    return transact(() -> consumeRefresh(hash, now, issue));
  }

  private <T> Optional<T> consumeRefresh(
      String hash, Instant now, Function<RefreshToken, T> issue) {
    var found = refresh.findById(hash);
    if (found.isEmpty()) {
      return Optional.empty();
    }
    var e = found.get();
    // A family lock serializes old-token replay with issuance of every descendant.
    var revoked =
        jdbc.queryForObject(
            "SELECT revoked_at IS NOT NULL FROM refresh_families WHERE family_id = ? FOR UPDATE",
            Boolean.class,
            e.familyId);
    if (Boolean.TRUE.equals(revoked)) {
      return Optional.empty();
    }
    if (!e.expiresAt.isAfter(now)) {
      return Optional.empty();
    }
    if (refresh.rotate(hash, now) != 1) {
      revoke(e.familyId, now);
      return Optional.empty();
    }
    return Optional.of(
        issue.apply(
            new RefreshToken(
                e.hash,
                e.familyId,
                e.clientId,
                e.userId,
                split(e.scopes),
                e.nonce,
                e.authTime,
                e.expiresAt)));
  }

  private <T> T transact(Supplier<T> action) {
    // Preserve AccessResolver's repeatable-read snapshot even during token
    // issuance.
    // PostgreSQL can abort a contending transition with SQLSTATE 40001; retry the
    // entire transaction with a fresh persistence context, then observe replay.
    for (int attempt = 0; ; attempt++) {
      try {
        return transaction.execute(status -> action.get());
      } catch (PessimisticLockingFailureException exception) {
        if (attempt >= 2) {
          throw exception;
        }
      }
    }
  }

  private void revoke(java.util.UUID family, Instant now) {
    jdbc.update(
        "UPDATE refresh_families SET revoked_at = COALESCE(revoked_at, ?) WHERE family_id = ?",
        Timestamp.from(now),
        family);
  }

  private static String join(Set<String> scopes) {
    return scopes.stream().sorted().collect(java.util.stream.Collectors.joining(" "));
  }

  private static Set<String> split(String scopes) {
    return scopes.isEmpty() ? Set.of() : Set.copyOf(Arrays.asList(scopes.split(" ")));
  }
}
