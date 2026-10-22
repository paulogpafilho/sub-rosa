package dev.subrosa.oidc;

import dev.subrosa.domain.AuthorizationCode;
import dev.subrosa.domain.OAuthClient;
import dev.subrosa.domain.OAuthGrantStore;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Creates hashed authorization grants after session authentication. */
public final class AuthorizationService {
  private final OAuthGrantStore store;
  private final BearerSecrets secrets;
  private final Clock clock;
  private final OAuthSettings settings;

  /** Uses the supplied grant store, random credentials, clock and code lifetime. */
  public AuthorizationService(
      OAuthGrantStore store, BearerSecrets secrets, Clock clock, OAuthSettings settings) {
    this.store = store;
    this.secrets = secrets;
    this.clock = clock;
    this.settings = settings;
  }

  /** Issues a random authorization code and stores only its client-bound hash. */
  public String authorize(
      OAuthClient client,
      AuthorizationRequestValidator.Request request,
      UUID user,
      Instant authTime) {
    var code = secrets.generate();
    store.saveCode(
        new AuthorizationCode(
            BearerSecrets.hash(code),
            UUID.randomUUID(),
            client.clientId(),
            user,
            request.redirectUri(),
            request.scopes(),
            request.nonce(),
            request.challenge(),
            authTime,
            clock.instant().plus(settings.codeTtl())));
    return code;
  }
}
