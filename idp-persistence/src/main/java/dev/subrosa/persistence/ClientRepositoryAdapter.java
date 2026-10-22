package dev.subrosa.persistence;

import dev.subrosa.domain.ClientRepository;
import dev.subrosa.domain.OAuthClient;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Persists versioned OAuth registrations behind the client repository port. */
@Repository
@Transactional
public class ClientRepositoryAdapter implements ClientRepository {
  private final JpaOAuthClientRepository clients;

  /** Uses the supplied JPA client repository. */
  public ClientRepositoryAdapter(JpaOAuthClientRepository clients) {
    this.clients = clients;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<OAuthClient> findById(String id) {
    return clients.findById(id).map(this::map);
  }

  @Override
  @Transactional(readOnly = true)
  public List<OAuthClient> findAll() {
    return clients.findAll().stream().map(this::map).toList();
  }

  @Override
  public OAuthClient save(OAuthClient client) {
    var entity = new OAuthClientEntity();
    entity.id = client.clientId();
    entity.applicationId = client.applicationId();
    entity.secretHash = client.secretHash();
    entity.authMethod = client.authMethod().name();
    entity.grants = new HashSet<>(client.grantTypes());
    entity.scopes = new HashSet<>(client.scopes());
    entity.redirects = new HashSet<>(client.redirectUris());
    entity.accessTokenTtl = client.accessTokenTtl().toSeconds();
    entity.refreshTokenTtl = client.refreshTokenTtl().toSeconds();
    entity.version = client.version();
    return map(clients.saveAndFlush(entity));
  }

  private OAuthClient map(OAuthClientEntity entity) {
    return new OAuthClient(
        entity.id,
        entity.applicationId,
        entity.secretHash,
        OAuthClient.AuthMethod.valueOf(entity.authMethod),
        entity.grants,
        entity.scopes,
        entity.redirects,
        Duration.ofSeconds(entity.accessTokenTtl),
        Duration.ofSeconds(entity.refreshTokenTtl),
        entity.version);
  }
}
