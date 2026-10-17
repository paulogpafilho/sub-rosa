package dev.subrosa.domain;

import java.util.Objects;
import java.util.UUID;

public record Group(UUID id, String name, Long version) {
	public Group {
		Objects.requireNonNull(id);
		if (name == null || name.isBlank() || name.length() > 200) {
			throw new IllegalArgumentException("Invalid group name");
		}
	}
}
