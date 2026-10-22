package dev.subrosa.persistence;

import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface JpaGroupRepository extends JpaRepository<GroupEntity, UUID> {
  @Query(
      value =
          "SELECT g.* FROM groups g JOIN user_groups ug ON ug.group_id = g.id"
              + " WHERE ug.user_id = :userId",
      nativeQuery = true)
  Set<GroupEntity> findForUser(UUID userId);

  @Modifying
  @Query(
      value =
          "INSERT INTO user_groups (group_id, user_id) VALUES (:groupId, :userId)"
              + " ON CONFLICT DO NOTHING",
      nativeQuery = true)
  void addMember(UUID groupId, UUID userId);

  @Modifying
  @Query(
      value = "DELETE FROM user_groups WHERE group_id = :groupId AND user_id = :userId",
      nativeQuery = true)
  void removeMember(UUID groupId, UUID userId);
}
