package dev.subrosa.persistence;

import java.util.stream.Collectors;
import dev.subrosa.domain.Application;
import dev.subrosa.domain.Entitlement;
import dev.subrosa.domain.Group;
import dev.subrosa.domain.Role;

final class AccessMapper {
	private AccessMapper() {
	}

	static Application toDomain(ApplicationEntity entity) {
		return new Application(entity.id, entity.name, entity.audience, entity.description, entity.version);
	}

	static ApplicationEntity toEntity(Application value) {
		var entity = new ApplicationEntity();
		entity.id = value.id();
		entity.name = value.name();
		entity.audience = value.audience();
		entity.description = value.description();
		entity.version = value.version();
		return entity;
	}

	static Group toDomain(GroupEntity entity) {
		return new Group(entity.id, entity.name, entity.version);
	}

	static GroupEntity toEntity(Group value) {
		var entity = new GroupEntity();
		entity.id = value.id();
		entity.name = value.name();
		entity.version = value.version();
		return entity;
	}

	static Role toDomain(RoleEntity entity) {
		return new Role(entity.id, entity.applicationId, entity.name,
				entity.entitlements.stream().map(Entitlement::new).collect(Collectors.toSet()), entity.version);
	}

	static RoleEntity toEntity(Role value) {
		var entity = new RoleEntity();
		entity.id = value.id();
		entity.applicationId = value.applicationId();
		entity.name = value.name();
		entity.entitlements = value.entitlements().stream().map(Entitlement::value).collect(Collectors.toSet());
		entity.version = value.version();
		return entity;
	}
}
