package dev.subrosa.domain;

import java.util.Optional;
import java.util.UUID;

/** Persists application roles and manages idempotent user and group grants. */
public interface RoleRepository {
  /** Looks up a stored aggregate by its stable identifier. */
  Optional<Role> findById(UUID id);

  /** Persists a new role or updates the version most recently read. */
  Role save(Role role);

  /** Adds a direct user grant if it does not already exist. */
  void assignToUser(UUID roleId, UUID userId);

  /** Removes a direct user grant if it exists. */
  void revokeFromUser(UUID roleId, UUID userId);

  /** Adds a group role grant if it does not already exist. */
  void assignToGroup(UUID roleId, UUID groupId);

  /** Removes a group role grant if it exists. */
  void revokeFromGroup(UUID roleId, UUID groupId);
}
