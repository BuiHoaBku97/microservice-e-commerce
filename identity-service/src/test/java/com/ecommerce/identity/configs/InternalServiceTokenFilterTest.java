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
  private static final KeyPair NEXT_KEYS = keys();
  private static final KeyPair PAYMENT_KEYS = keys();
  private static final String CURRENT_KID = "order-service-key-1";

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
    filter(token(CURRENT_KID, KEYS, "order-service", "identity-service", "identity.checkout", 60));
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
            token(CURRENT_KID, KEYS, "order-service", "catalog-service", "identity.checkout", 60),
            token(CURRENT_KID, KEYS, "order-service", "identity-service", "wrong", 60),
            token(CURRENT_KID, KEYS, "order-service", "identity-service", "identity.checkout", -1),
            token(CURRENT_KID, KEYS, "payment-service", "identity-service", "identity.checkout", 60))) {
      SecurityContextHolder.clearContext();
      filter(token);
      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
  }

  @Test
  void acceptsOldAndNewKidsDuringRotationButRejectsUnknownKid() throws Exception {
    String ring =
        "next="
            + Base64.getEncoder().encodeToString(NEXT_KEYS.getPublic().getEncoded())
            + ";current="
            + Base64.getEncoder().encodeToString(KEYS.getPublic().getEncoded());
    var filter =
        new InternalServiceTokenFilter(
            ring, "current=order-service;next=order-service", "ecommerce-internal");
    filter(
        filter,
        token("current", KEYS, "order-service", "identity-service", "identity.checkout", 60));
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    SecurityContextHolder.clearContext();
    filter(
        filter,
        token("next", NEXT_KEYS, "order-service", "identity-service", "identity.checkout", 60));
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    SecurityContextHolder.clearContext();
    filter(
        filter,
        token("unknown", KEYS, "order-service", "identity-service", "identity.checkout", 60));
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void rejectsAuthorizedPaymentIdentityOnOrderOnlyCheckoutOperation() throws Exception {
    String ring =
        CURRENT_KID
            + "="
            + Base64.getEncoder().encodeToString(KEYS.getPublic().getEncoded())
            + ";payment-service-key-1="
            + Base64.getEncoder().encodeToString(PAYMENT_KEYS.getPublic().getEncoded());
    var filter =
        new InternalServiceTokenFilter(
            ring,
            CURRENT_KID + "=order-service;payment-service-key-1=payment-service",
            "ecommerce-internal");
    MockHttpServletResponse response =
        filter(
            filter,
            token(
                "payment-service-key-1",
                PAYMENT_KEYS,
                "payment-service",
                "identity-service",
                "identity.checkout",
                60));

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  private void filter(String token) throws Exception {
    var request = new MockHttpServletRequest("POST", "/internal/v1/users/current/checkout-context");
    request.setServletPath("/internal/v1/users/current/checkout-context");
    request.addHeader("X-Service-Authorization", "Bearer " + token);
    new InternalServiceTokenFilter(
            CURRENT_KID + "=" + Base64.getEncoder().encodeToString(KEYS.getPublic().getEncoded()),
            CURRENT_KID + "=order-service",
            "ecommerce-internal")
        .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
  }

  private MockHttpServletResponse filter(InternalServiceTokenFilter filter, String token)
      throws Exception {
    var request = new MockHttpServletRequest("POST", "/internal/v1/users/current/checkout-context");
    request.setServletPath("/internal/v1/users/current/checkout-context");
    request.addHeader("X-Service-Authorization", "Bearer " + token);
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response;
  }

  private String token(
      String kid,
      KeyPair pair,
      String subject,
      String audience,
      String scope,
      long ttlSeconds) {
    Instant now = Instant.now();
    return Jwts.builder()
        .header()
        .keyId(kid)
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
        .signWith(pair.getPrivate(), Jwts.SIG.RS256)
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
