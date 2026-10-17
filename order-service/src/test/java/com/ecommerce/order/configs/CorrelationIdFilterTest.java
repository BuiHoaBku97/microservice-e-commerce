package com.ecommerce.order.configs;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {
  private final CorrelationIdFilter filter = new CorrelationIdFilter();

  @Test
  void directCallCreatesResponseCorrelationAndClearsContext() throws Exception {
    var request = new MockHttpServletRequest("GET", "/api/v1/orders");
    var response = new MockHttpServletResponse();
    FilterChain chain =
        (req, res) -> assertThat(CorrelationContext.currentOrCreate()).isNotBlank();

    filter.doFilter(request, response, chain);

    assertThat(response.getHeader(CorrelationContext.HEADER)).isNotBlank();
    assertThat(org.slf4j.MDC.get("correlationId")).isNull();
  }

  @Test
  void preservesValidCorrelationHeader() throws Exception {
    var request = new MockHttpServletRequest("GET", "/api/v1/orders");
    request.addHeader(CorrelationContext.HEADER, "caller-correlation");
    var response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) ->
        assertThat(CorrelationContext.currentOrCreate()).isEqualTo("caller-correlation"));

    assertThat(response.getHeader(CorrelationContext.HEADER)).isEqualTo("caller-correlation");
  }
}
