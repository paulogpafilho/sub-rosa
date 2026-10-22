package dev.subrosa.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/** Spring Boot entry point wiring the identity provider's adapters. */
@SpringBootApplication(
    scanBasePackages = "dev.subrosa",
    exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties(IdpProperties.class)
public class SubRosaApplication {

  /** Starts the Spring Boot application. */
  public static void main(String[] args) {
    SpringApplication.run(SubRosaApplication.class, args);
  }
}
