package dev.subrosa.domain;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface AccessRepository {
	/**
	 * One consistent snapshot, including global memberships and
	 * application-filtered roles.
	 */
	Optional<Snapshot> findAccess(UUID userId, UUID applicationId);

	record Snapshot(boolean enabled, Set<Group> groups, Set<Role> roles) {
		public Snapshot {
			groups = Set.copyOf(groups);
			roles = Set.copyOf(roles);
		}
	}
}
