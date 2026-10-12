package com.ecommerce.catalog.configs;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InternalServiceTokenFilter extends OncePerRequestFilter {
  private static final String INTERNAL_PREFIX = "/internal/";
  private static final String SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";
  private static final SimpleGrantedAuthority INTERNAL_AUTHORITY =
      new SimpleGrantedAuthority("SCOPE_INTERNAL_SERVICE");

  private final byte[] expectedToken;

  public InternalServiceTokenFilter(
      @Value("${security.internal.service-token:}") String serviceToken) {
    this.expectedToken = serviceToken.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (request.getServletPath().startsWith(INTERNAL_PREFIX)
        && hasValidToken(request.getHeader(SERVICE_TOKEN_HEADER))) {
      var current = SecurityContextHolder.getContext().getAuthentication();
      var authorities = new java.util.ArrayList<SimpleGrantedAuthority>();
      if (current != null) {
        current.getAuthorities().stream()
            .map(authority -> new SimpleGrantedAuthority(authority.getAuthority()))
            .forEach(authorities::add);
      }
      authorities.add(INTERNAL_AUTHORITY);
      SecurityContextHolder.getContext()
          .setAuthentication(
              UsernamePasswordAuthenticationToken.authenticated(
                  current == null ? "internal-service" : current.getPrincipal(),
                  current == null ? null : current.getCredentials(),
                  authorities));
    }
    filterChain.doFilter(request, response);
  }

  private boolean hasValidToken(String presentedToken) {
    if (expectedToken.length == 0 || presentedToken == null) {
      return false;
    }
    return MessageDigest.isEqual(
        expectedToken, presentedToken.getBytes(StandardCharsets.UTF_8));
  }
}
