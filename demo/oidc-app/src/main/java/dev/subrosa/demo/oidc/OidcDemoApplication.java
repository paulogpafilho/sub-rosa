package dev.subrosa.demo.oidc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Independent OIDC client used to verify the provider's login flow. */
@SpringBootApplication
public class OidcDemoApplication {

  /** Starts the Spring Boot application. */
  public static void main(String[] args) {
    SpringApplication.run(OidcDemoApplication.class, args);
  }
}
