package dev.subrosa.domain;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Loads consistent snapshots of a user's application access. */
public interface AccessRepository {
  /** One consistent snapshot, including global memberships and application-filtered roles. */
  Optional<Snapshot> findAccess(UUID userId, UUID applicationId);

  /** User status, global memberships and roles from one database snapshot. */
  record Snapshot(boolean enabled, Set<Group> groups, Set<Role> roles) {
    public Snapshot {
      groups = Set.copyOf(groups);
      roles = Set.copyOf(roles);
    }
  }
}
