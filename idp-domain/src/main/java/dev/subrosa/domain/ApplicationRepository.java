package dev.subrosa.domain;

import java.util.Optional;
import java.util.UUID;

/** Persists application aggregates without exposing persistence models. */
public interface ApplicationRepository {
  /** Looks up a stored aggregate by its stable identifier. */
  Optional<Application> findById(UUID id);

  /** New aggregates have a null version; updates must carry the last read version. */
  Application save(Application application);
}
