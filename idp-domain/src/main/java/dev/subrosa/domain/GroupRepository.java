package dev.subrosa.domain;

import java.util.Optional;
import java.util.UUID;

public interface GroupRepository {
	Optional<Group> findById(UUID id);

	Group save(Group group);

	void addMember(UUID groupId, UUID userId);

	void removeMember(UUID groupId, UUID userId);
}
