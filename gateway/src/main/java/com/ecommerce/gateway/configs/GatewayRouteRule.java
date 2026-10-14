package com.ecommerce.gateway.configs;

import java.util.List;
import org.springframework.http.HttpMethod;

public final class GatewayRouteRule {
  public static final String ADMIN_PATH = "/api/v1/admin/**";
  public static final List<String> CUSTOMER_OR_ADMIN_PATHS =
      List.of("/api/v1/users/**", "/api/v1/notifications/**");
  public static final List<String> CUSTOMER_PATHS =
      List.of("/api/v1/cart/**", "/api/v1/orders/**");

  public static final List<Route> OPEN_API =
      List.of(
          new Route(HttpMethod.GET, "/actuator/health"),
          new Route(HttpMethod.POST, "/api/v1/auth/register"),
          new Route(HttpMethod.POST, "/api/v1/auth/login"),
          new Route(HttpMethod.POST, "/api/v1/auth/refresh"),
          new Route(HttpMethod.POST, "/api/v1/auth/logout"),
          new Route(HttpMethod.POST, "/api/v1/payments/mock/callback"),
          new Route(HttpMethod.GET, "/api/v1/categories/**"),
          new Route(HttpMethod.GET, "/api/v1/inventory/availability"),
          new Route(HttpMethod.GET, "/api/v1/products/**"));

  private GatewayRouteRule() {}

  public record Route(HttpMethod method, String path) {}
}
