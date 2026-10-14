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

The foundations integration suite starts PostgreSQL 16 and checks Flyway migration
validation and reruns, JDBC session persistence and cascading deletion, and HTTP
health/access rules.

## Local startup

Requires Docker with Compose. From the repository root:

Powershell:
```powershell
Copy-Item .env-example .env
# Edit .env and replace DB_PASSWORD with a random password before starting.
docker compose --env-file .env -f deploy/docker-compose.yml up --build -d
curl.exe http://idp.localtest.me:9000/actuator/health
```

Bash:
```bash
cp .env-example .env
# Edit .env and replace DB_PASSWORD with a random password before starting.
docker compose --env-file .env -f deploy/docker-compose.yml up --build -d
curl http://idp.localtest.me:9000/actuator/health
```

`.env-example` lists every current deployment
property, sample values, whether it is required, and code defaults. Compose reads
it via `--env-file`; direct Java startup imports it from the current working
directory. Environment variables override the file for direct Java startup.
Use plain `KEY=value` entries without quotes; random hex passwords avoid differing
Compose and Java properties escape/interpolation rules.

The sample uses port 9000 and issuer `http://idp.localtest.me:9000`. Keep `IDP_PORT`,
the port in `IDP_ISSUER`, and the database port/name in `IDP_DB_URL` consistent with
their other settings. `IDP_HOSTNAME` must match the issuer hostname; Compose adds
that hostname as a network alias so browsers and future demo containers use the
same issuer. The demos are not yet wired for login.

PostgreSQL is not published to the host. The IdP binds to host loopback only and
waits for database health before starting. Flyway creates the initial session
schema; Hibernate only validates mappings and Spring Session's own DDL initializer
is disabled. Later migrations add identity and protocol tables alongside their
features. `GET /actuator/health` reports `UP` when the database is reachable;
details are hidden and all other routes are denied until implemented. Console
logs use ECS JSON, and no generated default security account is enabled.

The `dev` profile permits **HTTP for local development only** and disables the
session cookie's `Secure` flag. Outside `dev`, the issuer must use HTTPS, requests
redirect to HTTPS, and session cookies are secure. Non-local deployment requires
a TLS reverse proxy and an explicit trusted forwarded-header setup; that deployment
is not included here. Never expose this local HTTP configuration publicly.

```shell
docker compose --env-file .env -f deploy/docker-compose.yml logs -f idp
docker compose --env-file .env -f deploy/docker-compose.yml down
```

The named PostgreSQL volume survives `down`. Changing database credentials in
`.env` does not change credentials in an existing PostgreSQL volume; update them
in PostgreSQL or intentionally recreate your development database separately.

For direct Java startup, set `IDP_DB_URL` to a reachable PostgreSQL database, then
run the packaged application from the root so `.env` is loaded:

```shell
mvn -pl idp-web -am package -DskipTests
java -jar idp-web/target/idp-web-0.1.0-SNAPSHOT.jar
```

CI runs the full `mvn verify` reactor on Temurin 21 with Docker available. The
multi-stage image builds with Maven/JDK 21 from the repository root and runs with
a Java 21 JRE as a non-root user. Tests run in CI and `mvn verify`; the image's
packaging stage skips test execution so it does not require Docker inside Docker.

OpenSAML is resolved from the Shibboleth release repository.  
Other dependencies and plugins use Maven Central.

