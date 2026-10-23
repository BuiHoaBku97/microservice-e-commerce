package com.ecommerce.identity.configs;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private final PublicKey publicKey;

  public JwtAuthenticationFilter(@Value("${security.jwt.public-key-base64}") String publicKeyBase64)
      throws Exception {
    publicKey =
        KeyFactory.getInstance("RSA")
            .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (authorization != null && authorization.startsWith("Bearer ")) {
      try {
        Claims claims =
            Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(authorization.substring(7))
                .getPayload();
        validateIssuerAndAudience(claims);
        if (claims.getExpiration() == null || claims.getExpiration().before(new Date())) {
          throw new IllegalArgumentException("JWT_EXPIRED");
        }
        String scope = claims.get("scope", String.class);
        var authorities =
            scope == null
                ? java.util.List.<SimpleGrantedAuthority>of()
                : Arrays.stream(scope.split("\\s+"))
                    .filter(value -> !value.isBlank())
                    .map(value -> new SimpleGrantedAuthority("SCOPE_" + value))
                    .toList();
        SecurityContextHolder.getContext()
            .setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                    claims.getSubject(), authorization.substring(7), authorities));
      } catch (Exception exception) {
        SecurityContextHolder.clearContext();
      }
    }
    filterChain.doFilter(request, response);
  }

  private void validateIssuerAndAudience(Claims claims) {
    if (!"ecommerce-identity".equals(claims.getIssuer())
        || claims.getAudience() == null
        || !claims.getAudience().contains("ecommerce-api")) {
      throw new IllegalArgumentException("JWT_ISSUER_OR_AUDIENCE_INVALID");
    }
  }
}
