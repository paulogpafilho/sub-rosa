package dev.subrosa.domain;

import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;

/** Resolves enabled users' application roles and effective entitlements. */
public final class AccessResolver {
  private final AccessRepository access;

  /** Creates a resolver using the supplied consistent-snapshot port. */
  public AccessResolver(AccessRepository access) {
    this.access = Objects.requireNonNull(access);
  }

  /** Returns deduplicated application access, rejecting missing or disabled users. */
  public EffectiveAccess resolve(UUID userId, UUID applicationId) {
    Objects.requireNonNull(userId);
    Objects.requireNonNull(applicationId);
    var snapshot =
        access
            .findAccess(userId, applicationId)
            .filter(AccessRepository.Snapshot::enabled)
            .orElseThrow(() -> new IllegalArgumentException("User unavailable"));
    var roles = new HashSet<String>();
    var entitlements = new HashSet<Entitlement>();
    var groups = new HashSet<String>();
    snapshot.groups().forEach(group -> groups.add(group.name()));
    snapshot.roles().stream()
        .filter(role -> role.applicationId().equals(applicationId))
        .forEach(
            role -> {
              roles.add(role.name());
              entitlements.addAll(role.entitlements());
            });
    return new EffectiveAccess(roles, entitlements, groups);
  }
}
