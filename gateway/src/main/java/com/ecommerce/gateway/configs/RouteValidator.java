package com.ecommerce.gateway.configs;

import org.springframework.http.server.PathContainer;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPatternParser;

@Component
public class RouteValidator {
  private final PathPatternParser pathPatternParser = PathPatternParser.defaultInstance;

  public boolean isOpenApi(ServerWebExchange exchange) {
    return GatewayRouteRule.OPEN_API.stream().anyMatch(rule -> matches(rule, exchange));
  }

  public boolean hasRouteAccess(ServerWebExchange exchange, Authentication authentication) {
    if (!isAdminRoute(exchange)) {
      return true;
    }
    return authentication.getAuthorities().stream()
        .anyMatch(authority -> "SCOPE_ADMIN".equals(authority.getAuthority()));
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
