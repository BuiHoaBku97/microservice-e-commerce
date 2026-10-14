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
  private static final KeyPair PAYMENT_KEYS = keys();
  private static final String KEY_ID = "order-service-key-1";

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void validServiceJwtGrantsCatalogScope() throws Exception {
    filter(token("order-service", "catalog-service", "catalog.checkout", 60));
    assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
        .extracting("authority")
        .contains("SCOPE_INTERNAL_SERVICE", "SCOPE_catalog.checkout");
  }

  @Test
  void invalidClaimsDoNotAuthenticate() throws Exception {
    for (String token :
        List.of(
            token("order-service", "identity-service", "catalog.checkout", 60),
            token("order-service", "catalog-service", "wrong", 60),
            token("order-service", "catalog-service", "catalog.checkout", -1),
            token("payment-service", "catalog-service", "catalog.checkout", 60))) {
      SecurityContextHolder.clearContext();
      filter(token);
      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
  }

  @Test
  void rejectsAuthorizedPaymentIdentityOnOrderOnlyCatalogOperation() throws Exception {
    String ring =
        KEY_ID
            + "="
            + Base64.getEncoder().encodeToString(KEYS.getPublic().getEncoded())
            + ";payment-service-key-1="
            + Base64.getEncoder().encodeToString(PAYMENT_KEYS.getPublic().getEncoded());
    var filter =
        new InternalServiceTokenFilter(
            ring,
            KEY_ID + "=order-service;payment-service-key-1=payment-service",
            "ecommerce-internal");
    MockHttpServletResponse response =
        filter(
            filter,
            token(
                "payment-service-key-1",
                PAYMENT_KEYS,
                "payment-service",
                "catalog-service",
                "catalog.checkout",
                60));

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  private void filter(String token) throws Exception {
    var request = new MockHttpServletRequest("POST", "/internal/v1/skus/checkout-items");
    request.setServletPath("/internal/v1/skus/checkout-items");
    request.addHeader("X-Service-Authorization", "Bearer " + token);
    new InternalServiceTokenFilter(
            KEY_ID + "=" + Base64.getEncoder().encodeToString(KEYS.getPublic().getEncoded()),
            KEY_ID + "=order-service",
            "ecommerce-internal")
        .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
  }

  private MockHttpServletResponse filter(InternalServiceTokenFilter filter, String token)
      throws Exception {
    var request = new MockHttpServletRequest("POST", "/internal/v1/skus/checkout-items");
    request.setServletPath("/internal/v1/skus/checkout-items");
    request.addHeader("X-Service-Authorization", "Bearer " + token);
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response;
  }

  private String token(String subject, String audience, String scope, long ttlSeconds) {
    return token(KEY_ID, KEYS, subject, audience, scope, ttlSeconds);
  }

  private String token(
      String keyId,
      KeyPair keys,
      String subject,
      String audience,
      String scope,
      long ttlSeconds) {
    Instant now = Instant.now();
    return Jwts.builder()
        .header()
        .keyId(keyId)
        .and()
        .issuer("ecommerce-internal")
        .subject(subject)
        .audience()
        .add(audience)
        .and()
        .claim("scope", scope)
        .id(UUID.randomUUID().toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(ttlSeconds)))
        .signWith(keys.getPrivate(), Jwts.SIG.RS256)
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
