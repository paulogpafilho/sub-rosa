package dev.subrosa.persistence;

import dev.subrosa.domain.AccessRepository;
import dev.subrosa.domain.Entitlement;
import dev.subrosa.domain.Role;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Loads application access through repeatable-read JPA projections. */
@Repository
public class AccessRepositoryAdapter implements AccessRepository {
  private final JpaUserRepository users;
  private final JpaGroupRepository groups;
  private final JpaRoleRepository roles;

  /** Uses status, membership and role projections to load consistent access snapshots. */
  public AccessRepositoryAdapter(
      JpaUserRepository users, JpaGroupRepository groups, JpaRoleRepository roles) {
    this.users = users;
    this.groups = groups;
    this.roles = roles;
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Optional<Snapshot> findAccess(UUID userId, UUID applicationId) {
    var enabled = users.findEnabledById(userId);
    if (enabled.isEmpty()) {
      return Optional.empty();
    }
    if (!enabled.get()) {
      return Optional.of(new Snapshot(false, java.util.Set.of(), java.util.Set.of()));
    }
    var memberships =
        groups.findForUser(userId).stream().map(AccessMapper::toDomain).collect(Collectors.toSet());
    var byRole =
        roles.findAccess(userId, applicationId).stream()
            .collect(Collectors.groupingBy(RoleAccessRow::getId));
    var resolved = new HashSet<Role>();
    byRole.forEach(
        (id, rows) -> {
          var first = rows.getFirst();
          var entitlements =
              rows.stream()
                  .map(RoleAccessRow::getEntitlement)
                  .filter(java.util.Objects::nonNull)
                  .map(Entitlement::new)
                  .collect(Collectors.toSet());
          resolved.add(
              new Role(
                  id, first.getApplicationId(), first.getName(), entitlements, first.getVersion()));
        });
    return Optional.of(new Snapshot(true, memberships, resolved));
  }
}
