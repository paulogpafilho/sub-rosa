package dev.subrosa.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "oauth_clients")
class OAuthClientEntity {
  protected OAuthClientEntity() {}

  @Id
  @Column(name = "client_id", length = 200)
  String id;

  @Column(nullable = false)
  UUID applicationId;

  @Column(length = 64)
  String secretHash;

  @Column(nullable = false, length = 32)
  String authMethod;

  @Column(nullable = false)
  long accessTokenTtl;

  @Column(nullable = false)
  long refreshTokenTtl;

  @Version Long version;

  @ElementCollection
  @CollectionTable(name = "oauth_client_grants", joinColumns = @JoinColumn(name = "client_id"))
  @Column(name = "grant_type", length = 32)
  Set<String> grants = new HashSet<>();

  @ElementCollection
  @CollectionTable(name = "oauth_client_scopes", joinColumns = @JoinColumn(name = "client_id"))
  @Column(name = "scope", length = 32)
  Set<String> scopes = new HashSet<>();

  @ElementCollection
  @CollectionTable(
      name = "oauth_client_redirect_uris",
      joinColumns = @JoinColumn(name = "client_id"))
  @Column(name = "uri", length = 2000)
  Set<String> redirects = new HashSet<>();
}
