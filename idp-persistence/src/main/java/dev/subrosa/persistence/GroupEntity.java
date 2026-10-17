package dev.subrosa.persistence;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "groups")
class GroupEntity {
	protected GroupEntity() {
	}
	@Id
	UUID id;
	@Column(nullable = false, unique = true, length = 200)
	String name;
	@Version
	Long version;
}
