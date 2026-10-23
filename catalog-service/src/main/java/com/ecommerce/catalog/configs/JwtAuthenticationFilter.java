package com.ecommerce.catalog.configs;

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

  public JwtAuthenticationFilter(
      @Value("${spring.security.jwt.public-key-base64}") String publicKeyBase64) throws Exception {
    byte[] encodedKey = Base64.getDecoder().decode(publicKeyBase64);
    X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encodedKey);
    this.publicKey = KeyFactory.getInstance("RSA").generatePublic(keySpec);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);

    if (authorization != null && authorization.startsWith("Bearer ")) {
      try {
        String token = authorization.substring(7);
        Claims claims = parseClaims(token);
        validateIssuerAndAudience(claims);

        if (claims.getExpiration() == null || claims.getExpiration().before(new Date())) {
          throw new IllegalArgumentException("JWT_EXPIRED");
        }

        var authorities =
            Arrays.stream(claims.get("scope", String.class).split("\\s+"))
                .filter(role -> !role.isBlank())
                .map(role -> new SimpleGrantedAuthority("SCOPE_" + role))
                .toList();

        var authentication =
            UsernamePasswordAuthenticationToken.authenticated(
                claims.getSubject(), token, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
      } catch (Exception exception) {
        SecurityContextHolder.clearContext();
      }
    }

    filterChain.doFilter(request, response);
  }

  private Claims parseClaims(String token) {
    return Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token).getPayload();
  }

  private void validateIssuerAndAudience(Claims claims) {
    if (!"ecommerce-identity".equals(claims.getIssuer())
        || claims.getAudience() == null
        || !claims.getAudience().contains("ecommerce-api")) {
      throw new IllegalArgumentException("JWT_ISSUER_OR_AUDIENCE_INVALID");
    }
  }
}
