package dev.subrosa.persistence;

import java.util.Optional;
import java.util.UUID;
import dev.subrosa.domain.Application;
import dev.subrosa.domain.ApplicationRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class ApplicationRepositoryAdapter implements ApplicationRepository {
	private final JpaApplicationRepository applications;

	public ApplicationRepositoryAdapter(JpaApplicationRepository applications) {
		this.applications = applications;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Application> findById(UUID id) {
		return applications.findById(id).map(AccessMapper::toDomain);
	}

	@Override
	public Application save(Application application) {
		return AccessMapper.toDomain(applications.saveAndFlush(AccessMapper.toEntity(application)));
	}
}
