package com.ecommerce.identity.configs;

import com.ecommerce.identity.models.entities.UserStatus;
import com.ecommerce.identity.repositories.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ActiveAccountFilter extends OncePerRequestFilter {
  private final UserRepository userRepository;

  public ActiveAccountFilter(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getServletPath();
    return path.startsWith("/api/v1/auth/")
        || path.startsWith("/internal/")
        || path.equals("/actuator/health");
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
      boolean active =
          userRepository
              .findById(userId)
              .map(user -> user.getStatus() == UserStatus.ACTIVE)
              .orElse(false);
      if (!active) {
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "ACCOUNT_NOT_ACTIVE");
        return;
      }
      chain.doFilter(request, response);
    } catch (NumberFormatException exception) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "INVALID_USER_IDENTITY");
    }
  }
}
