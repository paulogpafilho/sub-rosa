package dev.subrosa.persistence;

import dev.subrosa.domain.Role;
import dev.subrosa.domain.RoleRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Persists roles, entitlements and idempotent access assignments through JPA. */
@Repository
@Transactional
public class RoleRepositoryAdapter implements RoleRepository {
  private final JpaRoleRepository roles;

  /** Uses the supplied JPA role repository. */
  public RoleRepositoryAdapter(JpaRoleRepository roles) {
    this.roles = roles;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Role> findById(UUID id) {
    return roles.findById(id).map(AccessMapper::toDomain);
  }

  @Override
  public Role save(Role role) {
    return AccessMapper.toDomain(roles.saveAndFlush(AccessMapper.toEntity(role)));
  }

  @Override
  public void assignToUser(UUID roleId, UUID userId) {
    roles.assignToUser(roleId, userId);
  }

  @Override
  public void revokeFromUser(UUID roleId, UUID userId) {
    roles.revokeFromUser(roleId, userId);
  }

  @Override
  public void assignToGroup(UUID roleId, UUID groupId) {
    roles.assignToGroup(roleId, groupId);
  }

  @Override
  public void revokeFromGroup(UUID roleId, UUID groupId) {
    roles.revokeFromGroup(roleId, groupId);
  }
}
