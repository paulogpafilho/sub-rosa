package dev.subrosa.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "applications")
class ApplicationEntity {
  protected ApplicationEntity() {}

  @Id UUID id;

  @Column(nullable = false, unique = true, length = 200)
  String name;

  @Column(nullable = false, unique = true, length = 500)
  String audience;

  @Column(length = 2000)
  String description;

  @Version Long version;
}
