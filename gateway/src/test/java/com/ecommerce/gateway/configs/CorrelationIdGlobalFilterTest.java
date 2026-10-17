package com.ecommerce.gateway.configs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class CorrelationIdGlobalFilterTest {
  private final CorrelationIdGlobalFilter filter = new CorrelationIdGlobalFilter();

  @Test
  void createsAndReturnsCorrelationWhenHeaderIsMissing() {
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders").build());
    AtomicReference<ServerWebExchange> routed = new AtomicReference<>();
    GatewayFilterChain chain = value -> { routed.set(value); return Mono.empty(); };

    filter.filter(exchange, chain).block();

    String correlation = routed.get().getRequest().getHeaders().getFirst("X-Correlation-ID");
    assertThat(correlation).isNotBlank();
    assertThat(exchange.getResponse().getHeaders().getFirst("X-Correlation-ID"))
        .isEqualTo(correlation);
  }

  @Test
  void preservesValidCallerCorrelation() {
    var exchange = MockServerWebExchange.from(
        MockServerHttpRequest.get("/api/v1/orders")
            .header("X-Correlation-ID", "caller-correlation")
            .build());
    AtomicReference<ServerWebExchange> routed = new AtomicReference<>();

    filter.filter(exchange, value -> { routed.set(value); return Mono.empty(); }).block();

    assertThat(routed.get().getRequest().getHeaders().getFirst("X-Correlation-ID"))
        .isEqualTo("caller-correlation");
  }
}
