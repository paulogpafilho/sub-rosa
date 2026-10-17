package dev.subrosa.domain;

import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository {
	Optional<Application> findById(UUID id);

	/**
	 * New aggregates have a null version; updates must carry the last read version.
	 */
	Application save(Application application);
}
