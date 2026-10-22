package dev.subrosa.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface JpaOAuthClientRepository extends JpaRepository<OAuthClientEntity, String> {}
