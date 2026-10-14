package com.ecommerce.identity.configs;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
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
  private final String issuer;

  @Autowired
  public InternalServiceTokenFilter(
      @Value(
              "${security.service-jwt.public-keys:${security.service-jwt.public-key-base64:${security.jwt.public-key-base64}}}")
          String configuredKeyRing,
      @Value("${security.service-jwt.issuer:ecommerce-internal}") String issuer)
      throws Exception {
    publicKeys = parseKeys(configuredKeyRing);
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
      Claims claims =
          Jwts.parser()
              .keyLocator(this::locateKey)
              .requireIssuer(issuer)
              .build()
              .parseSignedClaims(serviceAuthorizationHeader.substring(7))
              .getPayload();
      String scope = claims.get("scope", String.class);
      String requiredScope =
          request.getServletPath().endsWith("/status") ? "identity.status" : "identity.checkout";
      if (!claims.getAudience().contains("identity-service")) {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "INVALID_SERVICE_AUDIENCE");
        return;
      }
      if (scope == null || Arrays.stream(scope.split("\\s+")).noneMatch(requiredScope::equals)) {
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "INSUFFICIENT_SERVICE_SCOPE");
        return;
      }
      var currentAuthentication = SecurityContextHolder.getContext().getAuthentication();
      List<SimpleGrantedAuthority> authorities = new ArrayList<>();
      if (currentAuthentication != null)
        currentAuthentication
            .getAuthorities()
            .forEach(a -> authorities.add(new SimpleGrantedAuthority(a.getAuthority())));
      authorities.add(new SimpleGrantedAuthority("SCOPE_INTERNAL_SERVICE"));
      authorities.add(new SimpleGrantedAuthority("SCOPE_" + requiredScope));
      var authentication =
          UsernamePasswordAuthenticationToken.authenticated(
              currentAuthentication == null
                  ? claims.getSubject()
                  : currentAuthentication.getPrincipal(),
              null,
              authorities);
      authentication.setDetails(Map.of("service", claims.getSubject(), "jti", claims.getId()));
      SecurityContextHolder.getContext().setAuthentication(authentication);
    } catch (Exception exception) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "INVALID_SERVICE_TOKEN");
      return;
    }
    chain.doFilter(request, response);
  }

  private PublicKey locateKey(io.jsonwebtoken.Header serviceAuthorizationHeader) {
    String keyId = String.valueOf(serviceAuthorizationHeader.get("kid"));
    PublicKey locatedPublicKey = publicKeys.get(keyId);
    if (locatedPublicKey == null && publicKeys.size() == 1) {
      locatedPublicKey = publicKeys.values().iterator().next();
    }
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
}
