package com.ecommerce.payment.configs;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
  private final PublicKey publicKey;

  public JwtAuthenticationFilter(
      @Value("${spring.security.jwt.public-key-base64}") String publicKeyBase64) throws Exception {
    publicKey =
        KeyFactory.getInstance("RSA")
            .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith("Bearer ")) {
      try {
        Claims claims =
            Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(header.substring(7))
                .getPayload();
        if (claims.getExpiration() == null || claims.getExpiration().before(new Date())) {
          throw new IllegalArgumentException("JWT đã hết hạn");
        }
        String scope = claims.get("scope", String.class);
        var authorities =
            Arrays.stream(scope == null ? new String[0] : scope.split("\\s+"))
                .filter(role -> !role.isBlank())
                .map(role -> new SimpleGrantedAuthority("SCOPE_" + role))
                .toList();
        SecurityContextHolder.getContext()
            .setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                    claims.getSubject(), header.substring(7), authorities));
      } catch (Exception exception) {
        log.debug("Không thể xác thực JWT: {}", exception.getMessage());
        SecurityContextHolder.clearContext();
      }
    }
    chain.doFilter(request, response);
  }
}
