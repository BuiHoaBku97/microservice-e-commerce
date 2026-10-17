package com.ecommerce.gateway.configs;

import java.util.UUID;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {
  public static final String HEADER = "X-Correlation-ID";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String supplied = exchange.getRequest().getHeaders().getFirst(HEADER);
    String correlationId = isValid(supplied) ? supplied : UUID.randomUUID().toString();
    ServerHttpRequest request =
        exchange.getRequest().mutate().headers(headers -> headers.set(HEADER, correlationId)).build();
    ServerWebExchange correlated = exchange.mutate().request(request).build();
    correlated.getResponse().getHeaders().set(HEADER, correlationId);
    return chain.filter(correlated).contextWrite(context -> context.put("correlationId", correlationId));
  }

  private boolean isValid(String value) {
    return value != null && !value.isBlank() && value.length() <= 128;
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
