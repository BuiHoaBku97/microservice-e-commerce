package com.ecommerce.gateway.configs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthenticationGlobalFilter implements GlobalFilter, Ordered {
  private final PublicKey publicKey;
  private final RouteValidator routeValidator;
  private final ObjectMapper objectMapper;

  public JwtAuthenticationGlobalFilter(
      @Value("${spring.security.jwt.public-key-base64}") String publicKeyBase64,
      RouteValidator routeValidator,
      ObjectMapper objectMapper)
      throws Exception {
    this.publicKey =
        KeyFactory.getInstance("RSA")
            .generatePublic(
                new X509EncodedKeySpec(java.util.Base64.getDecoder().decode(publicKeyBase64)));
    this.routeValidator = routeValidator;
    this.objectMapper = objectMapper;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (routeValidator.isOpenApi(exchange)) {
      return chain.filter(exchange);
    }

    String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (header == null || !header.startsWith("Bearer ")) {
      return complete(
          exchange,
          HttpStatus.UNAUTHORIZED,
          "UNAUTHORIZED",
          "Y\u00eau c\u1ea7u x\u00e1c th\u1ef1c h\u1ee3p l\u1ec7");
    }

    try {
      Claims claims = authenticate(header.substring(7));
      if (!routeValidator.hasRouteAccess(exchange, claims)) {
        return complete(
            exchange,
            HttpStatus.FORBIDDEN,
            "FORBIDDEN",
            "B\u1ea1n kh\u00f4ng c\u00f3 quy\u1ec1n truy c\u1eadp");
      }
      return chain.filter(exchange);
    } catch (Exception exception) {
      return complete(
          exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "JWT kh\u00f4ng h\u1ee3p l\u1ec7");
    }
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }

  private Claims authenticate(String token) {
    Claims claims =
        Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token).getPayload();
    if (claims.getExpiration() == null || claims.getExpiration().before(new Date())) {
      throw new IllegalArgumentException("JWT kh\u00f4ng h\u1ee3p l\u1ec7");
    }
    return claims;
  }

  private Mono<Void> complete(
      ServerWebExchange exchange, HttpStatus status, String code, String message) {
    GatewayErrorResponse body =
        new GatewayErrorResponse(code, message, null, exchange.getRequest().getId());
    exchange.getResponse().setStatusCode(status);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
    try {
      byte[] payload = objectMapper.writeValueAsBytes(body);
      DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(payload);
      return exchange.getResponse().writeWith(Mono.just(buffer));
    } catch (JsonProcessingException exception) {
      return exchange.getResponse().setComplete();
    }
  }
}
