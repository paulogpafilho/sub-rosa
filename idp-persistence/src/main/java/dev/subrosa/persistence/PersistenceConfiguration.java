package dev.subrosa.persistence;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Enables scanning of the persistence adapter's JPA entities and repositories. */
@Configuration(proxyBeanMethods = false)
@EntityScan(basePackageClasses = UserEntity.class)
@EnableJpaRepositories(basePackageClasses = JpaUserRepository.class)
public class PersistenceConfiguration {}
