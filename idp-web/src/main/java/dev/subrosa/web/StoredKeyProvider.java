package dev.subrosa.web;

import dev.subrosa.domain.KeyProvider;
import dev.subrosa.domain.SigningKeyRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

final class StoredKeyProvider implements KeyProvider {
  private final SigningKeyRepository repository;
  private final SigningKeyCodec codec;
  private final Clock clock;

  StoredKeyProvider(SigningKeyRepository repository, SigningKeyCodec codec, Clock clock) {
    this.repository = repository;
    this.codec = codec;
    this.clock = clock;
  }

  void initialize() {
    codec.credentials(repository.initialize(() -> codec.generate(clock.instant())));
  }

  void rotate(Duration retention) {
    if (retention.compareTo(Duration.ofMinutes(11)) <= 0) {
      throw new IllegalArgumentException(
          "Retain retired keys longer than the ten-minute token ceiling"
              + " plus one-minute maximum skew");
    }
    var now = clock.instant();
    repository.rotate(() -> codec.generate(now), now, now.plus(retention));
  }

  @Override
  public SigningCredentials activeSigningKey() {
    return codec.credentials(
        repository.active().orElseThrow(() -> new IllegalStateException("No active signing key")));
  }

  @Override
  public List<PublicKey> verificationKeys() {
    return repository.publishedAt(clock.instant()).stream().map(codec::publicKey).toList();
  }
}
