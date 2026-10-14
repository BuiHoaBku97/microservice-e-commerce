package com.ecommerce.identity.models.entities;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class RefreshTokenSessionTest {
  @Test
  void rotationKeepsSessionAndLinksReplacement() {
    User user = new User("a@b.com", "hash", "A", Set.of(RoleName.CUSTOMER));
    UUID sid = UUID.randomUUID();
    RefreshToken oldToken = new RefreshToken(user, "old", Instant.now().plusSeconds(60), sid);
    RefreshToken replacement = new RefreshToken(user, "new", Instant.now().plusSeconds(60), sid);
    oldToken.replaceWith(replacement);
    assertThat(oldToken.getSessionId()).isEqualTo(replacement.getSessionId());
    assertThat(oldToken.getReplacedBy()).isSameAs(replacement);
    assertThat(oldToken.getRevokedAt()).isNotNull();
  }
}
