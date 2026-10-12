package com.ecommerce.catalog.configs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class InternalServiceTokenFilterTest {
  @AfterEach
  void clearContext() { SecurityContextHolder.clearContext(); }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"wrong-token"})
  void filter_shouldNotGrantInternalScope_whenTokenIsMissingOrWrong(String token) throws Exception {
    var user = UsernamePasswordAuthenticationToken.authenticated("user-1", null,
        List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    SecurityContextHolder.getContext().setAuthentication(user);
    filter("/internal/checkout-context", "test-token", token);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(user);
    assertThat(user.getAuthorities()).extracting("authority").doesNotContain("SCOPE_INTERNAL_SERVICE");
  }

  @Test
  void filter_shouldKeepUserAndGrantScope_whenInternalTokenIsValid() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated("user-1", null,
            List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
    filter("/internal/checkout-context", "test-token", "test-token");
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    assertThat(authentication.getPrincipal()).isEqualTo("user-1");
    assertThat(authentication.getAuthorities()).extracting("authority")
        .containsExactlyInAnyOrder("ROLE_CUSTOMER", "SCOPE_INTERNAL_SERVICE");
  }

  @Test
  void filter_shouldNotGrantScope_whenConfigurationIsEmptyOrPathIsPublic() throws Exception {
    filter("/internal/checkout-context", "", "");
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    filter("/api/v1/products", "test-token", "test-token");
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  private void filter(String path, String expected, String presented) throws Exception {
    var request = new MockHttpServletRequest("GET", path);
    request.setServletPath(path);
    if (presented != null) request.addHeader("X-Internal-Service-Token", presented);
    new InternalServiceTokenFilter(expected)
        .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
  }
}
