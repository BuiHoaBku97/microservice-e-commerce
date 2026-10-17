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
  private final Map<String, PublicKey> publicKeys;
  private final Map<String, String> keyOwners;
  private final String issuer;

  @Autowired
  public InternalServiceTokenFilter(
      @Value("${security.service-jwt.public-keys}") String configuredKeyRing,
      @Value("${security.service-jwt.key-owners}") String configuredKeyOwners,
      @Value("${security.service-jwt.issuer:ecommerce-internal}") String issuer)
      throws Exception {
    publicKeys = parseKeys(configuredKeyRing);
    keyOwners = parseKeyOwners(configuredKeyOwners);
    if (!publicKeys.keySet().equals(keyOwners.keySet())) {
      throw new IllegalArgumentException("SERVICE_JWT_KEY_OWNER_MISMATCH");
    }
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
    String serviceAuthorizationHeader = request.getHeader("X-Service-Authorization");
    if (serviceAuthorizationHeader == null || !serviceAuthorizationHeader.startsWith("Bearer ")) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "INVALID_SERVICE_TOKEN");
      return;
    }
    try {
      var signedJwt =
          Jwts.parser()
              .keyLocator(this::locateKey)
              .requireIssuer(issuer)
              .build()
              .parseSignedClaims(serviceAuthorizationHeader.substring(7));
      Claims claims = signedJwt.getPayload();
      String keyId = signedJwt.getHeader().getKeyId();
      String caller = keyOwners.get(keyId);
      String scope = claims.get("scope", String.class);
      String requiredScope =
          "GET".equals(request.getMethod()) ? "catalog.read" : "catalog.checkout";
      if (caller == null || !caller.equals(claims.getSubject())) {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "INVALID_SERVICE_IDENTITY");
        return;
      }
      if (!claims.getAudience().contains("catalog-service")) {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "INVALID_SERVICE_AUDIENCE");
        return;
      }
      if (scope == null
          || Arrays.stream(scope.split("\\s+")).noneMatch(requiredScope::equals)) {
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "INSUFFICIENT_SERVICE_SCOPE");
        return;
      }
      if (!"order-service".equals(caller)) {
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "SERVICE_CALLER_NOT_ALLOWED");
        return;
      }
      SecurityContextHolder.getContext()
          .setAuthentication(
              UsernamePasswordAuthenticationToken.authenticated(
                  claims.getSubject(),
                  null,
                  List.of(
                      new SimpleGrantedAuthority("SCOPE_INTERNAL_SERVICE"),
                      new SimpleGrantedAuthority("SCOPE_" + requiredScope))));
    } catch (Exception exception) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "INVALID_SERVICE_TOKEN");
      return;
    }
    chain.doFilter(request, response);
  }

  private PublicKey locateKey(io.jsonwebtoken.Header serviceAuthorizationHeader) {
    String keyId = String.valueOf(serviceAuthorizationHeader.get("kid"));
    PublicKey locatedPublicKey = publicKeys.get(keyId);
    if (locatedPublicKey == null) {
      throw new SecurityException("UNKNOWN_SERVICE_KEY");
    }
    return locatedPublicKey;
  }

  private Map<String, PublicKey> parseKeys(String configuredKeys) throws Exception {
    Map<String, PublicKey> parsedPublicKeys = new LinkedHashMap<>();
    for (String entry : configuredKeys.split(";")) {
      String[] keyEntryParts = entry.split("=", 2);
      String keyId = keyEntryParts.length == 2 ? keyEntryParts[0].trim() : "default";
      String publicKeyBase64 =
          keyEntryParts.length == 2 ? keyEntryParts[1].trim() : keyEntryParts[0].trim();
      parsedPublicKeys.put(
          keyId,
          KeyFactory.getInstance("RSA")
              .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64))));
    }
    return Map.copyOf(parsedPublicKeys);
  }

  private Map<String, String> parseKeyOwners(String configuredOwners) {
    Map<String, String> parsedOwners = new LinkedHashMap<>();
    for (String entry : configuredOwners.split(";")) {
      String[] ownerEntryParts = entry.split("=", 2);
      if (ownerEntryParts.length != 2
          || ownerEntryParts[0].isBlank()
          || ownerEntryParts[1].isBlank()) {
        throw new IllegalArgumentException("INVALID_SERVICE_JWT_KEY_OWNER");
      }
      parsedOwners.put(ownerEntryParts[0].trim(), ownerEntryParts[1].trim());
    }
    return Map.copyOf(parsedOwners);
  }
}
