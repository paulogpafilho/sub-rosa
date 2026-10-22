package dev.subrosa.domain;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Application-scoped role and its immutable set of entitlements. */
public record Role(
    UUID id, UUID applicationId, String name, Set<Entitlement> entitlements, Long version) {
  /** Validates the role's application binding and copies its entitlements. */
  public Role {
    Objects.requireNonNull(id);
    Objects.requireNonNull(applicationId);
    if (name == null || name.isBlank() || name.length() > 200) {
      throw new IllegalArgumentException("Invalid role name");
    }
    entitlements = Set.copyOf(entitlements);
  }
}
