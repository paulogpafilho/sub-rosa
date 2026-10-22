package dev.subrosa.persistence;

import java.util.UUID;

interface RoleAccessRow {
  UUID getId();

  UUID getApplicationId();

  String getName();

  Long getVersion();

  String getEntitlement();
}
