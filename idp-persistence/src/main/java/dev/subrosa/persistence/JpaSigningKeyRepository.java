package dev.subrosa.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface JpaSigningKeyRepository extends JpaRepository<SigningKeyEntity, String> {
  Optional<SigningKeyEntity> findByStatus(String status);

  @Query(
      "select k from SigningKeyEntity k where k.status = 'ACTIVE'"
          + " or (k.status = 'RETIRING' and k.retireAt > :now) order by k.createdAt, k.kid")
  List<SigningKeyEntity> publishedAt(Instant now);
}
