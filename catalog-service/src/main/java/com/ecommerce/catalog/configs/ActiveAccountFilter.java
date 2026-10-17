package com.ecommerce.catalog.configs;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@ConditionalOnProperty(
    name = "security.active-account.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ActiveAccountFilter extends OncePerRequestFilter {
  private final ServiceJwtProvider serviceJwtProvider;
  private final HttpClient httpClient;
  private final String identityUrl;

  public ActiveAccountFilter(
      ServiceJwtProvider serviceJwtProvider,
      @Value("${security.active-account.identity-url:http://localhost:8081}") String identityUrl) {
    this.serviceJwtProvider = serviceJwtProvider;
    this.identityUrl = identityUrl;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return "POST".equals(request.getMethod())
        && "/internal/v1/skus/checkout-items".equals(request.getServletPath());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      chain.doFilter(request, response);
      return;
    }
    try {
      long userId = Long.parseLong(authentication.getName());
      HttpRequest accountStatusRequest =
          HttpRequest.newBuilder()
              .uri(URI.create(identityUrl + "/internal/v1/users/" + userId + "/status"))
              .timeout(Duration.ofSeconds(3))
              .header(
                  "X-Service-Authorization",
                  "Bearer " + serviceJwtProvider.issue("identity-service", "identity.status"))
              .GET()
              .build();
      HttpResponse<String> accountStatusResponse =
          httpClient.send(accountStatusRequest, HttpResponse.BodyHandlers.ofString());
      if (accountStatusResponse.statusCode() == 200
          && "ACTIVE".equals(accountStatusResponse.body())) {
        chain.doFilter(request, response);
        return;
      }
      response.sendError(HttpServletResponse.SC_FORBIDDEN, "ACCOUNT_NOT_ACTIVE");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "IDENTITY_UNAVAILABLE");
    } catch (Exception exception) {
      response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "IDENTITY_UNAVAILABLE");
    }
  }
}
