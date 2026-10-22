package dev.subrosa.web;

import dev.subrosa.domain.AuthenticationService;
import dev.subrosa.domain.PasswordHasher;
import dev.subrosa.domain.User;
import dev.subrosa.domain.UserRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityProperties.class)
class IdentityConfiguration {
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  PasswordHasher passwordHasher(IdentityProperties settings) {
    return new BcryptPasswordHasher(settings.bcryptCost(), settings.passwordMaxBytes());
  }

  @Bean
  AuthenticationService authenticationService(
      UserRepository users, PasswordHasher passwords, Clock clock, IdentityProperties settings) {
    return new AuthenticationService(
        users, passwords, clock, settings.lockoutFailures(), settings.lockoutDuration());
  }

  @Bean
  ApplicationRunner bootstrapAdministrator(
      UserRepository users, PasswordHasher passwords, Clock clock, IdentityProperties settings) {
    return arguments -> {
      if (!users.hasUsers()) {
        if (settings.adminUser() == null
            || settings.adminUser().isBlank()
            || settings.adminUser().length() > 200
            || settings.adminPassword() == null
            || settings.adminPassword().isEmpty()) {
          throw new IllegalStateException(
              "An empty identity database requires IDP_ADMIN_USER and IDP_ADMIN_PASSWORD");
        }
        users.createFirst(
            new User(
                UUID.randomUUID(),
                settings.adminUser(),
                null,
                passwords.hash(settings.adminPassword()),
                true,
                0,
                null,
                clock.instant()));
      }
    };
  }

  @Bean
  IpLoginThrottle ipLoginThrottle(IdentityProperties settings, Clock clock) {
    return new IpLoginThrottle(
        settings.ipAttempts(), settings.ipWindow(), settings.ipCapacity(), clock);
  }
}
