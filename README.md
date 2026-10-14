# Sub-Rosa

A Java 21 identity provider for testing and demo-purposes.

## Modules

| Module | Responsibility |
| --- | --- |
| `idp-domain` | Framework-free domain objects, services, and ports |
| `idp-oidc` | OAuth/OIDC logic; domain and Nimbus JOSE+JWT |
| `idp-saml` | SAML logic; domain and OpenSAML |
| `idp-persistence` | JPA adapters and Flyway migrations |
| `idp-web` | Spring Boot controllers, security, templates, and wiring |
| `demo/oidc-app` | Independent Spring Security OIDC client |
| `demo/saml-app` | Independent Spring Security SAML service provider |

The repository root is the Maven aggregator.  
Production dependencies are declared in individual modules; the parent only manages versions.  
The domain has no production dependencies.  
The demos do not depend on the IdP modules.

## Build

Requires JDK 21 or newer and Maven 3.9 or newer.  
Compilation targets Java 21.

```shell
mvn verify
```

Formatting and Checkstyle run during `validate`.  
Apply Java formatting with:

```shell
mvn spotless:apply
```

Use the root POM, including for individual modules:

```shell
mvn -pl idp-oidc -am verify
```

Surefire runs unit tests (`*Test`); Failsafe runs integration tests (`*IT`) during
`verify`.  
Testcontainers tests require Docker.

OpenSAML is resolved from the Shibboleth release repository.  
Other dependencies and plugins use Maven Central.

