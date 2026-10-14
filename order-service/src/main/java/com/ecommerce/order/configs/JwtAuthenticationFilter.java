package com.ecommerce.order.configs;

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

  public JwtAuthenticationFilter(
      @Value("${spring.security.jwt.public-key-base64}") String publicKeyBase64) throws Exception {
    this.publicKey =
        KeyFactory.getInstance("RSA")
            .generatePublic(
                new X509EncodedKeySpec(java.util.Base64.getDecoder().decode(publicKeyBase64)));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith("Bearer ")) {
      try {
        String token = header.substring(7);
        Claims claims =
            Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token).getPayload();
        validateIssuerAndAudience(claims);
        if (claims.getExpiration() == null || claims.getExpiration().before(new Date()))
          throw new IllegalArgumentException();
        var authorities =
            Arrays.stream(claims.get("scope", String.class).split("\\s+"))
                .filter(role -> !role.isBlank())
                .map(role -> new SimpleGrantedAuthority("SCOPE_" + role))
                .toList();
        SecurityContextHolder.getContext()
            .setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                    claims.getSubject(), token, authorities));
      } catch (Exception ignored) {
        SecurityContextHolder.clearContext();
      }
    }
    chain.doFilter(request, response);
  }

  private void validateIssuerAndAudience(Claims claims) {
    if (!"ecommerce-identity".equals(claims.getIssuer())
        || claims.getAudience() == null
        || !claims.getAudience().contains("ecommerce-api")) {
      throw new IllegalArgumentException("JWT issuer or audience is invalid");
    }
  }
}
