package dev.subrosa.persistence;

import dev.subrosa.domain.Group;
import dev.subrosa.domain.GroupRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Persists groups and idempotent memberships through JPA. */
@Repository
@Transactional
public class GroupRepositoryAdapter implements GroupRepository {
  private final JpaGroupRepository groups;

  /** Uses the supplied JPA group repository. */
  public GroupRepositoryAdapter(JpaGroupRepository groups) {
    this.groups = groups;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Group> findById(UUID id) {
    return groups.findById(id).map(AccessMapper::toDomain);
  }

  @Override
  public Group save(Group group) {
    return AccessMapper.toDomain(groups.saveAndFlush(AccessMapper.toEntity(group)));
  }

  @Override
  public void addMember(UUID groupId, UUID userId) {
    groups.addMember(groupId, userId);
  }

  @Override
  public void removeMember(UUID groupId, UUID userId) {
    groups.removeMember(groupId, userId);
  }
}
