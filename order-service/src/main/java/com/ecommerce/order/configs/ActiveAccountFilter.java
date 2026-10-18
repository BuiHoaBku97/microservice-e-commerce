package com.ecommerce.order.configs;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@ConditionalOnProperty(
    name = "app.active-account.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ActiveAccountFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(ActiveAccountFilter.class);
  private final ServiceJwtProvider serviceJwtProvider;
  private final HttpClient httpClient;
  private final String identityUrl;

  public ActiveAccountFilter(
      ServiceJwtProvider serviceJwtProvider,
      @Value("${app.active-account.identity-url:http://localhost:8081}") String identityUrl) {
    this.serviceJwtProvider = serviceJwtProvider;
    this.identityUrl = identityUrl;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return "POST".equals(request.getMethod())
        && "/api/v1/orders/checkout".equals(request.getServletPath());
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
    HttpResponse<String> accountStatusResponse;
    try {
      HttpRequest accountStatusRequest =
          HttpRequest.newBuilder()
              .uri(URI.create(identityUrl + "/internal/v1/users/current/status"))
              .timeout(Duration.ofSeconds(3))
              .header(
                  "X-Service-Authorization",
                  "Bearer " + serviceJwtProvider.issue("identity-service", "identity.status"))
              .header("Authorization", request.getHeader("Authorization"))
              .header(CorrelationContext.HEADER, CorrelationContext.currentOrCreate())
              .GET()
              .build();
      accountStatusResponse = httpClient.send(accountStatusRequest, HttpResponse.BodyHandlers.ofString());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      log.warn("Identity status check interrupted for path {}", request.getServletPath(), exception);
      JsonErrorWriter.write(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "IDENTITY_UNAVAILABLE", request.getRequestId());
      return;
    } catch (Exception exception) {
      log.warn("Identity status check failed for path {}", request.getServletPath(), exception);
      JsonErrorWriter.write(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "IDENTITY_UNAVAILABLE", request.getRequestId());
      return;
    }
    if (accountStatusResponse.statusCode() != 200
        || !"ACTIVE".equals(accountStatusResponse.body())) {
      JsonErrorWriter.write(response, HttpServletResponse.SC_FORBIDDEN, "ACCOUNT_NOT_ACTIVE", request.getRequestId());
      return;
    }
    chain.doFilter(request, response);
  }
}
