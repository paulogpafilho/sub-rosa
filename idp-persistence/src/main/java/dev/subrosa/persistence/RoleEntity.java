package dev.subrosa.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
    name = "roles",
    uniqueConstraints = @UniqueConstraint(columnNames = {"application_id", "name"}))
class RoleEntity {
  protected RoleEntity() {}

  @Id UUID id;

  @Column(name = "application_id", nullable = false)
  UUID applicationId;

  @Column(nullable = false, length = 200)
  String name;

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "role_entitlements", joinColumns = @JoinColumn(name = "role_id"))
  @Column(name = "entitlement", nullable = false, length = 200)
  Set<String> entitlements = new HashSet<>();

  @Version Long version;
}
