package dev.subrosa.demo.saml;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point for the independent SAML demo service provider. */
@SpringBootApplication
public class SamlDemoApplication {

  /** Starts the Spring Boot application. */
  public static void main(String[] args) {
    SpringApplication.run(SamlDemoApplication.class, args);
  }
}
