package com.ecommerce.gateway.configs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;
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
    if (header == null || header.isBlank()) {
      return complete(
          exchange,
          HttpStatus.UNAUTHORIZED,
          "AUTHORIZATION_REQUIRED",
          "Yêu cầu xác thực là bắt buộc");
    }
    if (!header.startsWith("Bearer ")) {
      return complete(
          exchange,
          HttpStatus.UNAUTHORIZED,
          "AUTHORIZATION_SCHEME_INVALID",
          "Authorization phải sử dụng Bearer token");
    }

    try {
      String token = header.substring(7).trim();
      if (token.isEmpty()) {
        return complete(
            exchange, HttpStatus.UNAUTHORIZED, "TOKEN_FORMAT_INVALID", "Token không đúng định dạng");
      }
      Claims claims = authenticate(token);
      if (!routeValidator.hasRouteAccess(exchange, claims)) {
        return complete(
            exchange,
            HttpStatus.FORBIDDEN,
            "FORBIDDEN",
            "B\u1ea1n kh\u00f4ng c\u00f3 quy\u1ec1n truy c\u1eadp");
      }
      exchange.getAttributes().put("authenticatedUserId", claims.getSubject());
      return chain.filter(exchange);
    } catch (JwtAuthenticationException exception) {
      return complete(exchange, HttpStatus.UNAUTHORIZED, exception.code, exception.message);
    } catch (Exception exception) {
      return complete(
          exchange, HttpStatus.UNAUTHORIZED, "TOKEN_INVALID", "Token đăng nhập không hợp lệ");
    }
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 1;
  }

  private Claims authenticate(String token) {
    Claims claims;
    try {
      claims = Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token).getPayload();
    } catch (ExpiredJwtException exception) {
      throw new JwtAuthenticationException("JWT_EXPIRED", "Token đã hết hạn");
    } catch (MalformedJwtException | UnsupportedJwtException exception) {
      throw new JwtAuthenticationException("TOKEN_FORMAT_INVALID", "Token không đúng định dạng");
    } catch (SignatureException exception) {
      throw new JwtAuthenticationException("TOKEN_SIGNATURE_INVALID", "Chữ ký token không hợp lệ");
    } catch (JwtException exception) {
      throw new JwtAuthenticationException("TOKEN_INVALID", "Token đăng nhập không hợp lệ");
    }
    if (claims.getExpiration() == null) {
      throw new JwtAuthenticationException("JWT_EXPIRATION_MISSING", "Token thiếu thời hạn sử dụng");
    }
    if (claims.getExpiration().before(new Date())) {
      throw new JwtAuthenticationException("JWT_EXPIRED", "Token đã hết hạn");
    }
    if (!"ecommerce-identity".equals(claims.getIssuer())
        || claims.getAudience() == null
        || !claims.getAudience().contains("ecommerce-api")) {
      throw new JwtAuthenticationException(
          "JWT_ISSUER_OR_AUDIENCE_INVALID", "Issuer hoặc audience của token không hợp lệ");
    }
    return claims;
  }

  private Mono<Void> complete(
      ServerWebExchange exchange, HttpStatus status, String code, String message) {
    GatewayErrorResponse body =
        new GatewayErrorResponse(
            code,
            message,
            null,
            exchange.getRequest().getHeaders().getFirst(CorrelationIdGlobalFilter.HEADER));
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

  private static final class JwtAuthenticationException extends RuntimeException {
    private final String code;
    private final String message;

    private JwtAuthenticationException(String code, String message) {
      this.code = code;
      this.message = message;
    }
  }
}
