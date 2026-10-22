package dev.subrosa.domain;

import java.util.Optional;
import java.util.UUID;

/** Persists groups and manages idempotent user memberships. */
public interface GroupRepository {
  /** Looks up a stored aggregate by its stable identifier. */
  Optional<Group> findById(UUID id);

  /** Persists a new group or updates the version most recently read. */
  Group save(Group group);

  /** Adds membership if it does not already exist. */
  void addMember(UUID groupId, UUID userId);

  /** Removes membership if it exists. */
  void removeMember(UUID groupId, UUID userId);
}
