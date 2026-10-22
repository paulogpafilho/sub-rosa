package dev.subrosa.domain;

import java.util.Objects;
import java.util.UUID;

/** Application identity and audience with an optimistic persistence version. */
public record Application(UUID id, String name, String audience, String description, Long version) {
  /** Validates application identity, audience and display metadata. */
  public Application {
    Objects.requireNonNull(id);
    if (name == null
        || name.isBlank()
        || name.length() > 200
        || audience == null
        || audience.isBlank()
        || audience.length() > 500
        || (description != null && description.length() > 2000)) {
      throw new IllegalArgumentException("Invalid application");
    }
  }
}
