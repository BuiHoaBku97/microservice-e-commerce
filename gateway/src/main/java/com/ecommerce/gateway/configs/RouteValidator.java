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
    if (!isAdminRoute(exchange)) {
      return true;
    }
    String scope = claims.get("scope", String.class);
    return scope != null && Arrays.asList(scope.split("\\s+")).contains("ADMIN");
  }

  private boolean isAdminRoute(ServerWebExchange exchange) {
    return pathPatternParser
        .parse(GatewayRouteRule.ADMIN_PATH)
        .matches(PathContainer.parsePath(exchange.getRequest().getPath().value()));
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
