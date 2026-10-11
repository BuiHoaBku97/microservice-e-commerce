package com.ecommerce.gateway.configs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class JwtAuthenticationGlobalFilterTest {
  private JwtAuthenticationGlobalFilter filter;
  private KeyPair keyPair;

  @BeforeEach
  void setUp() throws Exception {
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(2048);
    keyPair = keyPairGenerator.generateKeyPair();
    filter =
        new JwtAuthenticationGlobalFilter(
            Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()),
            new RouteValidator(),
            new ObjectMapper());
  }

  @Test
  void allowsOpenApiWithoutToken() {
    var exchange = exchange(HttpMethod.POST, "/api/v1/auth/login");
    AtomicBoolean chainCalled = new AtomicBoolean();

    filter.filter(exchange, next(chainCalled)).block();

    assertTrue(chainCalled.get());
  }

  @Test
  void rejectsProtectedRequestWithoutToken() {
    var exchange = exchange(HttpMethod.GET, "/api/v1/orders");
    AtomicBoolean chainCalled = new AtomicBoolean();

    filter.filter(exchange, next(chainCalled)).block();

    assertEquals(401, exchange.getResponse().getStatusCode().value());
    assertFalse(chainCalled.get());
  }

  @Test
  void rejectsUserOnAdminRoute() {
    var exchange = exchange(HttpMethod.POST, "/api/v1/admin/products", token("USER"));
    AtomicBoolean chainCalled = new AtomicBoolean();

    filter.filter(exchange, next(chainCalled)).block();

    assertEquals(403, exchange.getResponse().getStatusCode().value());
    assertFalse(chainCalled.get());
  }

  @Test
  void allowsAuthenticatedUserOnProtectedRoute() {
    var exchange = exchange(HttpMethod.GET, "/api/v1/orders");
    AtomicBoolean chainCalled = new AtomicBoolean();

    filter
        .filter(exchange(HttpMethod.GET, "/api/v1/orders", token("USER")), next(chainCalled))
        .block();

    assertTrue(chainCalled.get());
  }

  private MockServerWebExchange exchange(HttpMethod method, String path) {
    return MockServerWebExchange.from(MockServerHttpRequest.method(method, path).build());
  }

  @Test
  void allowsInventoryAvailabilityWithoutToken() {
    var exchange = exchange(HttpMethod.GET, "/api/v1/inventory/availability?skuIds=1,2");
    AtomicBoolean chainCalled = new AtomicBoolean();
    filter.filter(exchange, next(chainCalled)).block();
    assertTrue(chainCalled.get());
  }

  @Test
  void rejectsAnonymousInventoryWrite() {
    var exchange = exchange(HttpMethod.POST, "/api/v1/admin/inventory/1/receipts");
    AtomicBoolean chainCalled = new AtomicBoolean();
    filter.filter(exchange, next(chainCalled)).block();
    assertEquals(401, exchange.getResponse().getStatusCode().value());
    assertFalse(chainCalled.get());
  }

  @Test
  void rejectsCustomerInventoryWrite() {
    var exchange = exchange(HttpMethod.POST, "/api/v1/admin/inventory/1/receipts", token("CUSTOMER"));
    AtomicBoolean chainCalled = new AtomicBoolean();
    filter.filter(exchange, next(chainCalled)).block();
    assertEquals(403, exchange.getResponse().getStatusCode().value());
    assertFalse(chainCalled.get());
  }

  private MockServerWebExchange exchange(HttpMethod method, String path, String token) {
    return MockServerWebExchange.from(
        MockServerHttpRequest.method(method, path)
            .header("Authorization", "Bearer " + token)
            .build());
  }

  private GatewayFilterChainAdapter next(AtomicBoolean chainCalled) {
    return exchange -> {
      chainCalled.set(true);
      return Mono.empty();
    };
  }

  private String token(String scope) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject("1")
        .claim("scope", scope)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(60)))
        .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
        .compact();
  }

  @FunctionalInterface
  private interface GatewayFilterChainAdapter
      extends org.springframework.cloud.gateway.filter.GatewayFilterChain {
    @Override
    Mono<Void> filter(ServerWebExchange exchange);
  }
}
