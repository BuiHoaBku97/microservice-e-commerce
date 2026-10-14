package com.ecommerce.identity.configs;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import java.security.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class InternalServiceTokenFilterTest {
  private static final KeyPair KEYS = keys();

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void validServiceJwtKeepsVerifiedUserAndAddsScopes() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                "7", null, List.of(new SimpleGrantedAuthority("SCOPE_CUSTOMER"))));
    filter(token("identity-service", "identity.checkout", 60));
    assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
        .isEqualTo("7");
    assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
        .extracting("authority")
        .contains("SCOPE_INTERNAL_SERVICE", "SCOPE_identity.checkout", "SCOPE_CUSTOMER");
  }

  @Test
  void wrongAudienceScopeAndExpiryNeverGrantInternalScope() throws Exception {
    for (String token :
        List.of(
            token("catalog-service", "identity.checkout", 60),
            token("identity-service", "wrong", 60),
            token("identity-service", "identity.checkout", -1))) {
      SecurityContextHolder.clearContext();
      filter(token);
      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
  }

  private void filter(String token) throws Exception {
    var request = new MockHttpServletRequest("POST", "/internal/v1/users/current/checkout-context");
    request.setServletPath("/internal/v1/users/current/checkout-context");
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
      KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
      g.initialize(2048);
      return g.generateKeyPair();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
