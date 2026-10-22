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

All Java modules, including tests and demos, use
[Google Java Style](https://google.github.io/styleguide/javaguide.html).
Spotless pins google-java-format 1.28.0 with the GOOGLE style; Checkstyle uses its
bundled `google_checks.xml`. Both run during `validate`, and Checkstyle warnings
fail the build. `.editorconfig` provides matching two-space Java indentation.

Apply Java formatting with:

```shell
mvn spotless:apply
```

Check formatting and style without compiling or running tests:

```shell
mvn validate
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
key RETIRING and inserts a new active key. Retention must exceed the maximum
ten-minute token lifetime plus one-minute clock skew; use 24 hours for manual rotation. At `retire_at`, the old
key leaves JWKS and signature verification; its encrypted database history remains.
The domain permits final RETIRED status after that deadline; a cleanup scheduler
is deferred. RETIRED keys are never published.
The certificate is valid for ten years; rotate before expiry. Sharing the signing
key between OIDC and future SAML is a deliberate MVP simplification.

`JwtSignatures` uses Nimbus JOSE+JWT for RS256 signing and signature verification,
with a required `kid` resolved only against the local published keys. It rejects
unsigned tokens, other algorithms, unknown keys, tampering and critical headers.
This is a signature primitive: callers must still validate issuer, audience, times,
token type and protocol-specific claims. The OAuth endpoints below use these
primitives; the admin UI remains deferred.

Tests cover encryption authentication, wrong secrets, concurrent bootstrap,
database constraints, rollback on failed rotation, old signatures across rotation
and retirement, public JWKS verification, and malformed/forged JWTs. The adapter
APIs follow the [Nimbus RSA JWT example](https://connect2id.com/products/nimbus-jose-jwt/examples/jwt-with-rsa-signature)
and [Bouncy Castle certificate builder](https://downloads.bouncycastle.org/java/docs/bcpkix-jdk18on-javadoc/org/bouncycastle/cert/jcajce/JcaX509v3CertificateBuilder.html).

## OAuth 2.0 and OpenID Connect

Authorization code with required S256 PKCE, client credentials and rotating refresh
tokens are implemented in the framework-free `idp-oidc` module. Spring handles
HTTP, session login, CSRF and wiring. Discovery is at
`/.well-known/openid-configuration`; the other endpoints are `/oauth2/authorize`,
`/oauth2/token`, `/userinfo` and `/oauth2/jwks`. Authorization accepts GET and
CSRF-protected POST; token accepts form POST; userinfo accepts GET/POST with an
Authorization bearer header. Unimplemented routes remain denied.

Client authentication is explicitly registered as `none`, `client_secret_basic`
or `client_secret_post`. Public clients cannot use client credentials. Clients are
registered against one application, with exact callback strings, allowed grants,
allowed scopes and bounded lifetimes. Client updates carry an optimistic version.
Flyway V5 adds clients, codes, refresh families and refresh tokens; V1–V4 are unchanged.

Only explicitly trusted first-party clients are supported in this MVP. Registration
is the prior arrangement for consent, including offline access; there is no consent
UI or dynamic registration. Do not register arbitrary third-party applications.
`prompt=none`, `prompt=login` and `max_age` are supported. Unsupported prompts,
request objects, request URIs, claims requests and non-query response modes are
rejected. Unknown clients and missing/unregistered callbacks produce a local error,
never a redirect. Other errors return the original state to the trusted callback.
Authorization responses include `iss`. Login resumes only a validated request held
in the JDBC session; session fixation protection and absolute expiry still apply.

Codes expire after 60 seconds and bind the client, callback, scopes, PKCE, nonce
and authentication time. Codes and refresh tokens contain 256 random bits and are
stored only as SHA-256 hashes, as are generated client secrets. Transactional locks
and conditional updates serialize consumption/rotation with issuance. Code replay
revokes its refresh family; reuse of a rotated refresh token revokes every descendant.
Invalid bindings and issuance failures roll back consumption. Refresh descendants
retain the original absolute expiry and may narrow their scopes.

Access tokens use RS256 and `typ: at+jwt`, application audience, subject, client ID,
scope, timestamps and unique `jti`. Client-credentials subjects identify the client
and carry no user access grants. ID tokens use the client audience, `auth_time`
and original authorization nonce (omitted on refresh). Shared `ClaimsMapper` filters
profile, email, groups, roles and entitlements by scope; entitlements follow `roles`.
Userinfo separately checks signature, type, issuer, application audience, expiry,
issuance time, maximum lifetime, subject, client and the `openid` scope, and rejects
machine tokens and disabled/missing users. Self-contained access/ID tokens already
issued remain valid until expiry; replay revocation affects refresh credentials.

Default global ceilings are 60s codes, 10m access/ID tokens and 24h absolute refresh
tokens; clients may shorten token lifetimes. Configure shorter ceilings using
`IDP_CODE_TTL`, `IDP_TOKEN_TTL`, `IDP_REFRESH_TTL` (ISO-8601). `IDP_CLOCK_SKEW`
defaults to 30s and permits 0–60s. Rotation retention must exceed the ten-minute
token ceiling plus maximum one-minute skew (strictly greater than 11 minutes).
Token/userinfo responses disable caching. CORS is limited to token, userinfo,
discovery and JWKS, and only origins derived from registered callback URLs.

### Local demo

Compose now includes `oidc-demo`, an independent Spring Security public client
that automatically uses PKCE. Start/rebuild with the existing root `.env`:

```shell
docker compose --env-file .env -f deploy/docker-compose.yml up --build -d
```

Run the existing access-model seed first (using an existing enabled username),
then explicitly register the callback. For the default port:

```powershell
Get-Content deploy/seed-oauth.sql | docker compose --env-file .env -f deploy/docker-compose.yml exec -T postgres psql -U <DB_USER> -d <DB_NAME> -p <DB_PORT> -v seed_redirect_uri=http://localhost:8081/login/oauth2/code/invoice-demo
```

Use `http://localhost:8081/` to open the demo. After IdP login, the demo verifies
the ID token and displays claims. Its cookie uses a separate name from the IdP.
`OIDC_DEMO_PORT` changes the published/application port; register that exact callback
before using a different port or hostname. A repeated seed never adds another callback
or replaces an existing secret. These are local HTTP development settings.

To add the optional `invoice-machine` client, generate a secret locally and retain
the plaintext securely once; pass only its hash to `seed-oauth.sql` as
`seed_client_secret_hash`:

```shell
python -c "import secrets,hashlib; s=secrets.token_urlsafe(32); print('CLIENT_SECRET='+s); print('seed_client_secret_hash='+hashlib.sha256(s.encode()).hexdigest())"
```

Set `CLIENT_ID=invoice-machine`, `CLIENT_SECRET` and `IDP_ISSUER` in your local shell
and run `sh deploy/client-credentials.sh`. It intentionally prints the token response
for inspection; do not capture it in shared logs. Secrets cannot be recovered from
their hashes. Refresh credentials require both the refresh grant and `offline_access`.

Implementation references: [OAuth 2.0](https://www.rfc-editor.org/rfc/rfc6749),
[PKCE](https://www.rfc-editor.org/rfc/rfc7636),
[OpenID Connect Core](https://openid.net/specs/openid-connect-core-1_0.html) and
[OAuth security BCP](https://www.rfc-editor.org/rfc/rfc9700).
The OpenID conformance suite remains Phase 7 work; this implementation is not certified.

`IDP_BUILD_IMAGE` optionally overrides the Maven build-stage image (default
`maven:3.9.15-eclipse-temurin-21`). A trusted local build image with cached Maven
and Google formatter artifacts can be used during an upstream download outage;
all formatting and Checkstyle checks still run.

