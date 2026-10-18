package com.ecommerce.order.configs;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
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
  private HttpServer server;

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
    CorrelationContext.clear();
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void blocksAUserWhoseCurrentStatusIsLocked() throws Exception {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/internal/v1/users/current/status",
        exchange -> {
          byte[] body = "LOCKED".getBytes();
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();

    MockHttpServletResponse response =
        execute("http://localhost:" + server.getAddress().getPort(), "GET", "/api/v1/orders/3");

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentAsString()).contains("\"code\":\"ACCOUNT_NOT_ACTIVE\"")
        .contains("\"errors\":[]");
  }

  @Test
  void failsClosedWhenIdentityIsUnavailable() throws Exception {
    MockHttpServletResponse response = execute("http://127.0.0.1:1", "GET", "/api/v1/orders/3");

    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(response.getContentAsString()).contains("\"code\":\"IDENTITY_UNAVAILABLE\"")
        .contains("\"errors\":[]");
  }

  @Test
  void skipsIdentityStatusForCheckout() throws Exception {
    MockHttpServletResponse response =
        execute("http://127.0.0.1:1", "POST", "/api/v1/orders/checkout");

    assertThat(response.getStatus()).isEqualTo(200);
  }

  private MockHttpServletResponse execute(
      String identityUrl, String method, String servletPath) throws Exception {
    var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(2048);
    var keyPair = keyPairGenerator.generateKeyPair();
    var serviceJwtProvider =
        new ServiceJwtProvider(
            Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()),
            "ecommerce-internal",
            "order-service",
            "test-key",
            60);
    var filter = new ActiveAccountFilter(serviceJwtProvider, identityUrl);
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                "7", null, List.of(new SimpleGrantedAuthority("SCOPE_CUSTOMER"))));
    var request = new MockHttpServletRequest(method, servletPath);
    request.setServletPath(servletPath);
    request.addHeader("Authorization", "Bearer customer-token");
    var response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response;
  }
}
