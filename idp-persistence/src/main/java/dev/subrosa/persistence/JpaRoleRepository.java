package dev.subrosa.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface JpaRoleRepository extends JpaRepository<RoleEntity, UUID> {
  @Query(
      value =
          """
          SELECT r.id AS id, r.application_id AS "applicationId", r.name AS name,
                 r.version AS version, e.entitlement AS entitlement
          FROM roles r LEFT JOIN role_entitlements e ON e.role_id = r.id
          WHERE r.application_id = :applicationId AND (
              EXISTS (SELECT 1 FROM user_roles ur WHERE ur.role_id = r.id AND ur.user_id = :userId)
              OR EXISTS (SELECT 1 FROM group_roles gr
                         JOIN user_groups ug ON ug.group_id = gr.group_id
                         WHERE gr.role_id = r.id AND ug.user_id = :userId))
          """,
      nativeQuery = true)
  List<RoleAccessRow> findAccess(UUID userId, UUID applicationId);

  @Modifying
  @Query(
      value =
          "INSERT INTO user_roles (role_id, user_id) VALUES (:roleId, :userId)"
              + " ON CONFLICT DO NOTHING",
      nativeQuery = true)
  void assignToUser(UUID roleId, UUID userId);

  @Modifying
  @Query(
      value = "DELETE FROM user_roles WHERE role_id = :roleId AND user_id = :userId",
      nativeQuery = true)
  void revokeFromUser(UUID roleId, UUID userId);

  @Modifying
  @Query(
      value =
          "INSERT INTO group_roles (role_id, group_id) VALUES (:roleId, :groupId)"
              + " ON CONFLICT DO NOTHING",
      nativeQuery = true)
  void assignToGroup(UUID roleId, UUID groupId);

  @Modifying
  @Query(
      value = "DELETE FROM group_roles WHERE role_id = :roleId AND group_id = :groupId",
      nativeQuery = true)
  void revokeFromGroup(UUID roleId, UUID groupId);
}
