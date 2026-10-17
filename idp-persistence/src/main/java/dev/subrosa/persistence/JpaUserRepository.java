package dev.subrosa.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface JpaUserRepository extends JpaRepository<UserEntity, UUID> {
	@Query("select u.enabled from UserEntity u where u.id = :id")
	Optional<Boolean> findEnabledById(UUID id);

	Optional<UserEntity> findByUsername(String username);

	@Modifying
	@Query(value = """
			UPDATE users SET
			  failed_attempts = CASE WHEN locked_until IS NOT NULL THEN 1 ELSE failed_attempts + 1 END,
			  locked_until = CASE
			    WHEN (CASE WHEN locked_until IS NOT NULL THEN 1 ELSE failed_attempts + 1 END) >= :threshold
			      THEN CAST(:until AS timestamp with time zone) ELSE NULL END
			WHERE id = :id AND enabled AND (locked_until IS NULL OR locked_until <= :now)
			""", nativeQuery = true)
	int recordFailure(UUID id, Instant now, int threshold, Instant until);

	@Modifying
	@Query(value = """
			UPDATE users SET failed_attempts = 0, locked_until = NULL
			WHERE id = :id AND enabled AND password_hash = :hash AND failed_attempts = :failures
			  AND (locked_until IS NULL OR locked_until <= :now)
			""", nativeQuery = true)
	int recordSuccess(UUID id, String hash, int failures, Instant now);
}
