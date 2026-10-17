package com.ecommerce.catalog.configs;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class ActiveAccountFilterTest {
  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
    CorrelationContext.clear();
  }

  @Test
  void internalCheckoutSkipsIdentityEvenWhenCustomerAuthorizationWasPresent() throws Exception {
    MockHttpServletResponse response =
        execute("POST", "/internal/v1/skus/checkout-items", "http://127.0.0.1:1");

    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void otherAuthenticatedRequestsStillFailClosedWhenIdentityIsUnavailable() throws Exception {
    MockHttpServletResponse response =
        execute("POST", "/api/v1/admin/products", "http://127.0.0.1:1");

    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(response.getContentAsString()).contains("\"code\":\"IDENTITY_UNAVAILABLE\"")
        .contains("\"errors\":[]");
  }

  private MockHttpServletResponse execute(String method, String path, String identityUrl)
      throws Exception {
    var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(2048);
    var keyPair = keyPairGenerator.generateKeyPair();
    var provider =
        new ServiceJwtProvider(
            Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()),
            "ecommerce-internal",
            "test-key");
    var filter = new ActiveAccountFilter(provider, identityUrl);
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                "7", null, List.of(new SimpleGrantedAuthority("SCOPE_CUSTOMER"))));
    var request = new MockHttpServletRequest(method, path);
    request.setServletPath(path);
    var response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response;
  }
}
