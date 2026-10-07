package com.ecommerce.gateway.configs;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

@Component
public class RouteValidator {
  private final GatewaySecurityProperties properties;
  private final PathPatternParser pathPatternParser = PathPatternParser.defaultInstance;

  public RouteValidator(GatewaySecurityProperties properties) {
    this.properties = properties;
    validateRules(properties.getOpenApi());
    validateRules(properties.getRouteRules());
  }

  public boolean isOpenApi(ServerWebExchange exchange) {
    return matchesAny(properties.getOpenApi(), exchange);
  }

  public boolean hasRequiredAuthorities(ServerWebExchange exchange, Authentication authentication) {
    List<GatewaySecurityProperties.RouteRule> matchingRules =
        properties.getRouteRules().stream().filter(rule -> matches(rule, exchange)).toList();
    if (matchingRules.isEmpty()) {
      return true;
    }

    Set<String> grantedAuthorities =
        authentication.getAuthorities().stream()
            .map(granted -> granted.getAuthority())
            .collect(Collectors.toSet());
    return matchingRules.stream()
        .anyMatch(rule -> grantedAuthorities.containsAll(rule.getAuthorities()));
  }

  private boolean matchesAny(
      List<GatewaySecurityProperties.RouteRule> rules, ServerWebExchange exchange) {
    return rules.stream().anyMatch(rule -> matches(rule, exchange));
  }

  private boolean matches(GatewaySecurityProperties.RouteRule rule, ServerWebExchange exchange) {
    String method = rule.getMethod();
    boolean methodMatches =
        method == null
            || method.isBlank()
            || HttpMethod.valueOf(method).equals(exchange.getRequest().getMethod());
    PathPattern pathPattern = pathPatternParser.parse(rule.getPath());
    boolean pathMatches =
        pathPattern.matches(PathContainer.parsePath(exchange.getRequest().getPath().value()));
    return methodMatches && pathMatches;
  }

  private void validateRules(List<GatewaySecurityProperties.RouteRule> rules) {
    rules.forEach(
        rule -> {
          if (rule.getPath() == null || rule.getPath().isBlank()) {
            throw new IllegalArgumentException("Gateway route rule phải có path");
          }
          if (rule.getMethod() != null && !rule.getMethod().isBlank()) {
            HttpMethod.valueOf(rule.getMethod());
          }
          pathPatternParser.parse(rule.getPath());
        });
  }
}
