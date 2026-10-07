package com.ecommerce.gateway.configs;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthenticationGlobalFilter implements GlobalFilter, Ordered {
  private final PublicKey publicKey;
  private final RouteValidator routeValidator;

  public JwtAuthenticationGlobalFilter(
      @Value("${spring.security.jwt.public-key-base64}") String publicKeyBase64,
      RouteValidator routeValidator)
      throws Exception {
    this.publicKey =
        KeyFactory.getInstance("RSA")
            .generatePublic(
                new X509EncodedKeySpec(java.util.Base64.getDecoder().decode(publicKeyBase64)));
    this.routeValidator = routeValidator;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (routeValidator.isOpenApi(exchange)) {
      return chain.filter(exchange);
    }

    String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (header == null || !header.startsWith("Bearer ")) {
      return complete(exchange, HttpStatus.UNAUTHORIZED);
    }

    try {
      Authentication authentication = authenticate(header.substring(7));
      if (!routeValidator.hasRouteAccess(exchange, authentication)) {
        return complete(exchange, HttpStatus.FORBIDDEN);
      }
      return chain
          .filter(exchange)
          .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    } catch (Exception exception) {
      return complete(exchange, HttpStatus.UNAUTHORIZED);
    }
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }

  private Authentication authenticate(String token) {
    Claims claims =
        Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token).getPayload();
    if (claims.getExpiration() == null || claims.getExpiration().before(new Date())) {
      throw new IllegalArgumentException("JWT không hợp lệ");
    }

    String scope = claims.get("scope", String.class);
    var authorities =
        scope == null
            ? java.util.List.<SimpleGrantedAuthority>of()
            : Arrays.stream(scope.split("\\s+"))
                .filter(role -> !role.isBlank())
                .map(role -> new SimpleGrantedAuthority("SCOPE_" + role))
                .toList();
    return UsernamePasswordAuthenticationToken.authenticated(
        claims.getSubject(), token, authorities);
  }

  private Mono<Void> complete(ServerWebExchange exchange, HttpStatus status) {
    exchange.getResponse().setStatusCode(status);
    return exchange.getResponse().setComplete();
  }
}
