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
  private Claims customerClaims;
  private Claims adminClaims;

  @BeforeEach
  void setUp() {
    routeValidator = new RouteValidator();
    customerClaims = claimsWithScope("CUSTOMER");
    adminClaims = claimsWithScope("ADMIN");
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
  void allowsCustomerOnCustomerRoute() {
    var exchange = exchange(HttpMethod.GET, "/api/v1/orders");

    assertTrue(routeValidator.hasRouteAccess(exchange, customerClaims));
  }

  @Test
  void rejectsCustomerOnAdminRoute() {
    var exchange = exchange(HttpMethod.POST, "/api/v1/admin/products");

    assertFalse(routeValidator.hasRouteAccess(exchange, customerClaims));
  }

  @Test
  void allowsAdminOnAdminRoute() {
    var exchange = exchange(HttpMethod.POST, "/api/v1/admin/products");

    assertTrue(routeValidator.hasRouteAccess(exchange, adminClaims));
  }

  @Test
  void rejectsAdminOnCustomerRoute() {
    var exchange = exchange(HttpMethod.GET, "/api/v1/orders");

    assertFalse(routeValidator.hasRouteAccess(exchange, adminClaims));
  }

  @Test
  void allowsAdminOnSharedProfileAndNotificationRoutes() {
    assertTrue(
        routeValidator.hasRouteAccess(
            exchange(HttpMethod.GET, "/api/v1/users/me"), adminClaims));
    assertTrue(
        routeValidator.hasRouteAccess(
            exchange(HttpMethod.GET, "/api/v1/notifications"), adminClaims));
  }

  @Test
  void rejectsMissingScopeOnProtectedRoute() {
    var exchange = exchange(HttpMethod.GET, "/api/v1/orders");
    Claims claimsWithoutScope = Jwts.claims().subject("1").build();

    assertFalse(routeValidator.hasRouteAccess(exchange, claimsWithoutScope));
  }

  private MockServerWebExchange exchange(HttpMethod method, String path) {
    return MockServerWebExchange.from(MockServerHttpRequest.method(method, path).build());
  }

  @Test
  void opensOnlyGetOnExactInventoryAvailabilityPath() {
    assertTrue(
        routeValidator.isOpenApi(exchange(HttpMethod.GET, "/api/v1/inventory/availability")));
    assertFalse(
        routeValidator.isOpenApi(exchange(HttpMethod.POST, "/api/v1/inventory/availability")));
    assertFalse(
        routeValidator.isOpenApi(exchange(HttpMethod.GET, "/api/v1/inventory/availability/1")));
    assertFalse(routeValidator.isOpenApi(exchange(HttpMethod.GET, "/api/v1/admin/inventory")));
  }

  private Claims claimsWithScope(String scope) {
    return Jwts.claims()
        .subject("1")
        .add("scope", scope)
        .expiration(new Date(System.currentTimeMillis() + 60_000))
        .build();
  }
}
