package com.ecommerce.catalog.configs;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import java.security.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;

class InternalServiceTokenFilterTest {
  private static final KeyPair KEYS = keys();

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void validServiceJwtGrantsCatalogScope() throws Exception {
    filter(token("catalog-service", "catalog.checkout", 60));
    assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
        .extracting("authority")
        .contains("SCOPE_INTERNAL_SERVICE", "SCOPE_catalog.checkout");
  }

  @Test
  void invalidClaimsDoNotAuthenticate() throws Exception {
    for (String token :
        List.of(
            token("identity-service", "catalog.checkout", 60),
            token("catalog-service", "wrong", 60),
            token("catalog-service", "catalog.checkout", -1))) {
      SecurityContextHolder.clearContext();
      filter(token);
      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
  }

  private void filter(String token) throws Exception {
    var request = new MockHttpServletRequest("POST", "/internal/v1/skus/checkout-items");
    request.setServletPath("/internal/v1/skus/checkout-items");
    request.addHeader("X-Service-Authorization", "Bearer " + token);
    new InternalServiceTokenFilter(
            Base64.getEncoder().encodeToString(KEYS.getPublic().getEncoded()), "ecommerce-internal")
        .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
  }

  private String token(String audience, String scope, long ttlSeconds) {
    Instant now = Instant.now();
    return Jwts.builder()
        .issuer("ecommerce-internal")
        .subject("order-service")
        .audience()
        .add(audience)
        .and()
        .claim("scope", scope)
        .id(UUID.randomUUID().toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(ttlSeconds)))
        .signWith(KEYS.getPrivate(), Jwts.SIG.RS256)
        .compact();
  }

  private static KeyPair keys() {
    try {
      KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
      keyPairGenerator.initialize(2048);
      return keyPairGenerator.generateKeyPair();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
