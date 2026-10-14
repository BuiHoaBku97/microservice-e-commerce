package com.ecommerce.identity.configs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ecommerce.identity.models.entities.User;
import com.ecommerce.identity.repositories.UserRepository;
import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class ActiveAccountFilterTest {
  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void rejectsLockedAccountEvenWhenAccessTokenWasAlreadyAuthenticated() throws Exception {
    UserRepository users = mock(UserRepository.class);
    User locked = new User("locked@example.com", "hash", "Locked", java.util.Set.of());
    locked.lock();
    when(users.findById(7L)).thenReturn(Optional.of(locked));
    authenticate("7", "SCOPE_ADMIN");
    FilterChain chain = mock(FilterChain.class);
    MockHttpServletResponse response = new MockHttpServletResponse();

    new ActiveAccountFilter(users)
        .doFilter(request("/api/v1/admin/users"), response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
    verifyNoInteractions(chain);
  }

  @Test
  void allowsActiveAccount() throws Exception {
    UserRepository users = mock(UserRepository.class);
    User active = new User("active@example.com", "hash", "Active", java.util.Set.of());
    when(users.findById(7L)).thenReturn(Optional.of(active));
    authenticate("7", "SCOPE_CUSTOMER");
    FilterChain chain = mock(FilterChain.class);
    MockHttpServletRequest request = request("/api/v1/users/me");
    MockHttpServletResponse response = new MockHttpServletResponse();

    new ActiveAccountFilter(users).doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }

  @Test
  void skipsInternalRequestsBecauseTheyUseServiceAuthentication() throws Exception {
    UserRepository users = mock(UserRepository.class);
    FilterChain chain = mock(FilterChain.class);
    MockHttpServletRequest request = request("/internal/v1/users/7/status");
    MockHttpServletResponse response = new MockHttpServletResponse();

    new ActiveAccountFilter(users).doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verifyNoInteractions(users);
  }

  private void authenticate(String subject, String authority) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                subject, null, List.of(new SimpleGrantedAuthority(authority))));
  }

  private MockHttpServletRequest request(String path) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
    request.setServletPath(path);
    return request;
  }
}
