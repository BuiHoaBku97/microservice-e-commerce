package com.ecommerce.gateway.configs;

import io.jsonwebtoken.Claims;
import java.util.Arrays;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPatternParser;

@Component
public class RouteValidator {
  private final PathPatternParser pathPatternParser = PathPatternParser.defaultInstance;

  public boolean isOpenApi(ServerWebExchange exchange) {
    return GatewayRouteRule.OPEN_API.stream().anyMatch(rule -> matches(rule, exchange));
  }

  public boolean hasRouteAccess(ServerWebExchange exchange, Claims claims) {
    String scope = claims.get("scope", String.class);
    if (scope == null) {
      return false;
    }
    var scopes = Arrays.asList(scope.split("\\s+"));
    if (isAdminRoute(exchange)) {
      return scopes.contains("ADMIN");
    }
    if (isCustomerRoute(exchange)) {
      return scopes.contains("CUSTOMER");
    }
    return true;
  }

  private boolean isAdminRoute(ServerWebExchange exchange) {
    return pathPatternParser
        .parse(GatewayRouteRule.ADMIN_PATH)
        .matches(PathContainer.parsePath(exchange.getRequest().getPath().value()));
  }

  private boolean isCustomerRoute(ServerWebExchange exchange) {
    PathContainer requestPath = PathContainer.parsePath(exchange.getRequest().getPath().value());
    return GatewayRouteRule.CUSTOMER_PATHS.stream()
        .map(pathPatternParser::parse)
        .anyMatch(pattern -> pattern.matches(requestPath));
  }

  private boolean matches(GatewayRouteRule.Route rule, ServerWebExchange exchange) {
    boolean methodMatches = rule.method().equals(exchange.getRequest().getMethod());
    boolean pathMatches =
        pathPatternParser
            .parse(rule.path())
            .matches(PathContainer.parsePath(exchange.getRequest().getPath().value()));
    return methodMatches && pathMatches;
  }
}
