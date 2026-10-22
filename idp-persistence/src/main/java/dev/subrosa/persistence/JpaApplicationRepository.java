package dev.subrosa.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaApplicationRepository extends JpaRepository<ApplicationEntity, UUID> {}
