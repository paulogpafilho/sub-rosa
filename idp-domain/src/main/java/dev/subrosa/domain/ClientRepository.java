package dev.subrosa.domain;

import java.util.List;
import java.util.Optional;

/** Persists explicit OAuth registrations and their optimistic versions. */
public interface ClientRepository {
  /** Looks up a stored aggregate by its stable identifier. */
  Optional<OAuthClient> findById(String clientId);

  /** Returns registered clients, including their trusted callback origins. */
  List<OAuthClient> findAll();

  /** Persists a new registration or updates the version most recently read. */
  OAuthClient save(OAuthClient client);
}
