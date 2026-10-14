package com.ecommerce.catalog.configs;

import io.jsonwebtoken.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InternalServiceTokenFilter extends OncePerRequestFilter {
  private final PublicKey publicKey;
  private final String issuer;

  @Autowired
  public InternalServiceTokenFilter(
      @Value("${security.service-jwt.public-publicKeyBase64-base64:${spring.security.jwt.public-publicKeyBase64-base64}}")
          String publicKeyBase64,
      @Value("${security.service-jwt.issuer:ecommerce-internal}") String issuer)
      throws Exception {
    publicKey =
        KeyFactory.getInstance("RSA")
            .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)));
    this.issuer = issuer;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!request.getServletPath().startsWith("/internal/")) {
      chain.doFilter(request, response);
      return;
    }
    String header = request.getHeader("X-Service-Authorization");
    try {
      if (header == null || !header.startsWith("Bearer ")) throw new SecurityException();
      Claims claims =
          Jwts.parser()
              .verifyWith(publicKey)
              .requireIssuer(issuer)
              .build()
              .parseSignedClaims(header.substring(7))
              .getPayload();
      String scope = claims.get("scope", String.class);
      if (!claims.getAudience().contains("catalog-service")
          || scope == null
          || Arrays.stream(scope.split("\\s+")).noneMatch("catalog.checkout"::equals))
        throw new SecurityException();
      SecurityContextHolder.getContext()
          .setAuthentication(
              UsernamePasswordAuthenticationToken.authenticated(
                  claims.getSubject(),
                  null,
                  List.of(
                      new SimpleGrantedAuthority("SCOPE_INTERNAL_SERVICE"),
                      new SimpleGrantedAuthority("SCOPE_catalog.checkout"))));
    } catch (Exception exception) {
      /* keep any verified user context */
    }
    chain.doFilter(request, response);
  }
}
