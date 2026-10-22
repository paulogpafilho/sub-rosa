package dev.subrosa.persistence;

import dev.subrosa.domain.User;

final class UserMapper {
  private UserMapper() {}

  static UserEntity toEntity(User user) {
    var entity = new UserEntity();
    entity.id = user.id();
    entity.username = user.username();
    entity.email = user.email();
    entity.passwordHash = user.passwordHash();
    entity.enabled = user.enabled();
    entity.failedAttempts = user.failedAttempts();
    entity.lockedUntil = user.lockedUntil();
    entity.createdAt = user.createdAt();
    return entity;
  }

  static User toDomain(UserEntity entity) {
    return new User(
        entity.id,
        entity.username,
        entity.email,
        entity.passwordHash,
        entity.enabled,
        entity.failedAttempts,
        entity.lockedUntil,
        entity.createdAt);
  }
}
