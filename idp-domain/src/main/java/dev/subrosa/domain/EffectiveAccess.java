package dev.subrosa.domain;

import java.util.Set;

/** Groups describe global membership; roles and entitlements are application-scoped. */
public record EffectiveAccess(
    Set<String> roles, Set<Entitlement> entitlements, Set<String> groups) {
  /** Copies resolved grants and memberships into immutable sets. */
  public EffectiveAccess {
    roles = Set.copyOf(roles);
    entitlements = Set.copyOf(entitlements);
    groups = Set.copyOf(groups);
  }
}
