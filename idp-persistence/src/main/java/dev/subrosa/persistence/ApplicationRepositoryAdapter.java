package dev.subrosa.persistence;

import dev.subrosa.domain.Application;
import dev.subrosa.domain.ApplicationRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Maps application aggregates to and from package-private JPA entities. */
@Repository
@Transactional
public class ApplicationRepositoryAdapter implements ApplicationRepository {
  private final JpaApplicationRepository applications;

  /** Uses the supplied JPA application repository. */
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
