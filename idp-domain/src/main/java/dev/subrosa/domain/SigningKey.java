package dev.subrosa.domain;

import java.time.Instant;
import java.util.Objects;

/** Immutable stored snapshot. Private material is always encrypted. */
public record SigningKey(
    String kid,
    String privateKeyEncrypted,
    String publicPem,
    String certificatePem,
    Status status,
    Instant createdAt,
    Instant retireAt) {
  /** Signing-key lifecycle states controlling signing and publication. */
  public enum Status {
    ACTIVE,
    RETIRING,
    RETIRED
  }

  /** Validates required key material and the retirement lifecycle invariants. */
  public SigningKey {
    if (kid == null || !kid.matches("[a-zA-Z0-9-]{1,100}")) {
      throw new IllegalArgumentException("Invalid signing key ID");
    }
    Objects.requireNonNull(privateKeyEncrypted);
    Objects.requireNonNull(publicPem);
    Objects.requireNonNull(certificatePem);
    Objects.requireNonNull(status);
    Objects.requireNonNull(createdAt);
    if ((status == Status.ACTIVE && retireAt != null)
        || (status != Status.ACTIVE && (retireAt == null || !retireAt.isAfter(createdAt)))) {
      throw new IllegalArgumentException("Invalid signing key lifecycle");
    }
  }

  /** Returns a retiring snapshot with the supplied verification deadline. */
  public SigningKey beginRetirement(Instant until) {
    if (status != Status.ACTIVE) {
      throw new IllegalStateException("Only an active key can retire");
    }
    return new SigningKey(
        kid, privateKeyEncrypted, publicPem, certificatePem, Status.RETIRING, createdAt, until);
  }

  /** Returns a terminal retired snapshot after the publication deadline has elapsed. */
  public SigningKey retire(Instant now) {
    if (status != Status.RETIRING || now.isBefore(retireAt)) {
      throw new IllegalStateException("Retirement requires an elapsed verification grace period");
    }
    return new SigningKey(
        kid, privateKeyEncrypted, publicPem, certificatePem, Status.RETIRED, createdAt, retireAt);
  }

  /** Reports whether the key may be published and used for verification at the given time. */
  public boolean publishedAt(Instant now) {
    return status == Status.ACTIVE || (status == Status.RETIRING && now.isBefore(retireAt));
  }

  @Override
  public String toString() {
    return "SigningKey[kid=" + kid + ", status=" + status + "]";
  }
}
