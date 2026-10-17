package dev.subrosa.domain;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository {
	Optional<Role> findById(UUID id);

	Role save(Role role);

	void assignToUser(UUID roleId, UUID userId);

	void revokeFromUser(UUID roleId, UUID userId);

	void assignToGroup(UUID roleId, UUID groupId);

	void revokeFromGroup(UUID roleId, UUID groupId);
}
