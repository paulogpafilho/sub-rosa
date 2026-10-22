package dev.subrosa.domain;

import java.util.Objects;
import java.util.UUID;

/** Global user-membership group with an optimistic persistence version. */
public record Group(UUID id, String name, Long version) {
  /** Validates group identity and display name. */
  public Group {
    Objects.requireNonNull(id);
    if (name == null || name.isBlank() || name.length() > 200) {
      throw new IllegalArgumentException("Invalid group name");
    }
  }
}
