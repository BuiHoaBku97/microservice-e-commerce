package com.ecommerce.gateway.configs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class RouteValidatorTest {
  private RouteValidator routeValidator;
  private Claims userClaims;
  private Claims adminClaims;

  @BeforeEach
  void setUp() {
    routeValidator = new RouteValidator();
    userClaims = claimsWithScope("USER");
    adminClaims = claimsWithScope("USER ADMIN");
  }

  @Test
  void allowsConfiguredOpenApiWithoutAuthentication() {
    var exchange = exchange(HttpMethod.POST, "/api/v1/auth/login");

    assertTrue(routeValidator.isOpenApi(exchange));
  }

  @Test
  void protectsNonOpenApiRoute() {
    var exchange = exchange(HttpMethod.GET, "/api/v1/orders");

    assertFalse(routeValidator.isOpenApi(exchange));
  }

  @Test
  void allowsUserOnNonAdminProtectedRoute() {
    var exchange = exchange(HttpMethod.GET, "/api/v1/orders");

    assertTrue(routeValidator.hasRouteAccess(exchange, userClaims));
  }

  @Test
  void rejectsUserOnAdminRoute() {
    var exchange = exchange(HttpMethod.POST, "/api/v1/admin/products");

    assertFalse(routeValidator.hasRouteAccess(exchange, userClaims));
  }

  @Test
  void allowsAdminOnAdminRoute() {
    var exchange = exchange(HttpMethod.POST, "/api/v1/admin/products");

    assertTrue(routeValidator.hasRouteAccess(exchange, adminClaims));
  }

  private MockServerWebExchange exchange(HttpMethod method, String path) {
    return MockServerWebExchange.from(MockServerHttpRequest.method(method, path).build());
  }

  private Claims claimsWithScope(String scope) {
    return Jwts.claims()
        .subject("1")
        .add("scope", scope)
        .expiration(new Date(System.currentTimeMillis() + 60_000))
        .build();
  }
}
