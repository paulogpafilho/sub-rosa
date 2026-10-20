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

Integration tests start PostgreSQL 16 and check migrations, concurrent bootstrap
and account lockout, password handling, CSRF, session rotation, shared JDBC session
storage, logout, and idle/absolute expiry. Unit tests cover bcrypt byte limits and
the per-IP sliding window.

## Local startup

Requires Docker with Compose. From the repository root:

Powershell:
```powershell
Copy-Item .env-example .env
# Edit .env: replace both passwords and set IDP_KEY_ENCRYPTION_SECRET (see below).
docker compose --env-file .env -f deploy/docker-compose.yml up --build -d
curl.exe http://idp.localtest.me:9000/actuator/health
```

Bash:
```bash
cp .env-example .env
# Edit .env: replace both passwords and set IDP_KEY_ENCRYPTION_SECRET (see below).
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
details are hidden. Console
logs use ECS JSON, and no generated default security account is enabled.

Open `http://idp.localtest.me:9000/login` and use the bootstrap credentials from
your `.env`. The initial account is created only when no users exist; startup
fails if that database is empty and bootstrap credentials are missing. Concurrent
first starts serialize bootstrap creation. Later changes to these credentials do
not reset an existing account. The account has no admin-console role yet; that
console is a later phase.

Login requires CSRF, rotates the session ID, and redirects to the authenticated
home page. Sign out there with the CSRF-protected form. Tabs sharing the cookie
share the JDBC-backed session. Sessions expire after 30 idle minutes or eight
hours since authentication by default; activity cannot extend the absolute limit.
Cookies are HttpOnly and SameSite=Lax. Login, health, and JWKS are public; the home
page requires authentication and unimplemented routes are denied.

Passwords use salted bcrypt (cost 12 by default), with a maximum of 72 UTF-8
bytes, configurable downwards. Unknown users still perform a dummy bcrypt check;
unknown, disabled, locked and incorrect credentials use the same public error.
Five consecutive failures lock an account for 15 minutes. The next attempt after
lock expiry starts a fresh counter; successful authentication clears failures.
Database updates enforce lockout atomically under concurrent attempts.

A bounded, per-instance sliding window also limits all login attempts to 20 per
socket IP per minute. It ignores forwarding headers; behind a proxy, callers
share the proxy's limit unless a trusted forwarding setup is explicitly added.
The throttle resets on restart and is not a cluster-wide limit. Account locks
and sessions are shared in PostgreSQL. All limits are documented in `.env-example`.

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

## Access model

Applications own roles; roles contain validated, flat entitlements such as
`invoices:approve`. Users receive roles directly or through global groups.
There are no direct user entitlement grants. The stable subject is the user UUID.
`AccessResolver.resolve(userId, applicationId)` unions and deduplicates the requested
application's role names and entitlements. Groups describe all of the user's global
memberships, including groups without roles for that application. An enabled user
without grants receives empty roles/entitlements; a missing or disabled user is
rejected. An unknown application has no grants.

The persistence adapter reads a repeatable-read snapshot in three queries for an
enabled user, regardless of membership, role or entitlement count. Disabled and
missing users stop after the status query. Roles without entitlements are retained.
Applications, groups and roles carry optimistic versions; new snapshots use a null
version, and edits must retain the version last read. Membership and assignment
operations are idempotent additions/removals, enforced by composite primary keys.
Flyway V3 adds the access tables; V1 and V2 remain unchanged.

The development seed is explicit and repeatable. After starting the stack, choose
an existing enabled user and substitute your `.env` database values below:

```powershell
Get-Content deploy/seed.sql | docker compose --env-file .env -f deploy/docker-compose.yml exec -T postgres psql -U <DB_USER> -d <DB_NAME> -p <DB_PORT> -v seed_username=<existing-user>
```

```bash
docker compose --env-file .env -f deploy/docker-compose.yml exec -T postgres psql -U <DB_USER> -d <DB_NAME> -p <DB_PORT> -v seed_username=<existing-user> < deploy/seed.sql
```

This grants `Reader` directly and `Approver` through `Finance` for `Invoice Demo`
(`urn:subrosa:invoice-demo`), producing `invoices:read` and `invoices:approve`.
It requires an explicit existing enabled username and neither creates users nor
changes passwords. Rerunning adds missing demo grants without duplicates; it does
not reset edits to existing aggregate fields. Stable demo IDs are reserved by the
script; conflicting names/IDs should be resolved before seeding.

PostgreSQL integration tests cover grants, revocations, application isolation,
disabled users, schema constraints, optimistic versions and repeatable seeding.
Hibernate statistics assert exactly three access queries for zero, one and many
roles/groups/entitlements. Domain unit tests cover unions, validation and immutable
results.

## Signing keys and JWT primitives

Every startup requires `IDP_KEY_ENCRYPTION_SECRET`: exactly 64 hexadecimal
characters encoding 32 cryptographically random bytes, used directly as an
AES-256 key. There is no default or password derivation. Generate one locally,
then put the output in ignored `.env` (never commit or log it):

```powershell
$keyBytes = [byte[]]::new(32)
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($keyBytes)
$rng.Dispose()
[BitConverter]::ToString($keyBytes).Replace('-', '').ToLowerInvariant()
```

```bash
openssl rand -hex 32
```

Back up this secret securely alongside database backups, separately from the
database. Losing it makes stored signing keys unrecoverable. Changing it requires
an explicit decrypt/re-encrypt migration with both secrets; replacing the variable
alone makes startup fail. Never delete key history to work around a failed decrypt.

The first startup generates an RSA-2048 key and matching self-signed X.509 signing
certificate. Its `kid` is the lowercase hex SHA-256 thumbprint of the public key's
DER SubjectPublicKeyInfo. Generation is serialized across instances under a PostgreSQL
transaction lock. Flyway V4 adds `signing_keys`; V1–V3 remain unchanged. A partial
unique index enforces at most one ACTIVE key. The PKCS#8 private key is encrypted
using AES-256-GCM, a fresh 96-bit nonce and 128-bit authentication tag. The stored
`v1:` envelope contains base64(nonce || ciphertext || tag); authenticated data binds
the envelope to its key ID and format. Only public PEM and certificate are plaintext.
Startup checks decryption and matching private/public material before serving traffic.

`GET /oauth2/jwks` publishes only RSA public JWK fields (`kty`, `kid`, `use`, `alg`,
`n`, `e`). ACTIVE and unexpired RETIRING keys are included. Rotation is an internal
operation with no HTTP endpoint or scheduler yet: it atomically marks the previous
key RETIRING and inserts a new active key. Retention must exceed the planned maximum
ten-minute token lifetime; use 24 hours for manual rotation. At `retire_at`, the old
key leaves JWKS and signature verification; its encrypted database history remains.
The domain permits final RETIRED status after that deadline; a cleanup scheduler
is deferred. RETIRED keys are never published.
The certificate is valid for ten years; rotate before expiry. Sharing the signing
key between OIDC and future SAML is a deliberate MVP simplification.

`JwtSignatures` uses Nimbus JOSE+JWT for RS256 signing and signature verification,
with a required `kid` resolved only against the local published keys. It rejects
unsigned tokens, other algorithms, unknown keys, tampering and critical headers.
This is a signature primitive: callers must still validate issuer, audience, times,
token type and protocol-specific claims. No tokens are issued over HTTP yet;
authorization/token endpoints, discovery and the admin UI remain deferred.

Tests cover encryption authentication, wrong secrets, concurrent bootstrap,
database constraints, rollback on failed rotation, old signatures across rotation
and retirement, public JWKS verification, and malformed/forged JWTs. The adapter
APIs follow the [Nimbus RSA JWT example](https://connect2id.com/products/nimbus-jose-jwt/examples/jwt-with-rsa-signature)
and [Bouncy Castle certificate builder](https://downloads.bouncycastle.org/java/docs/bcpkix-jdk18on-javadoc/org/bouncycastle/cert/jcajce/JcaX509v3CertificateBuilder.html).

