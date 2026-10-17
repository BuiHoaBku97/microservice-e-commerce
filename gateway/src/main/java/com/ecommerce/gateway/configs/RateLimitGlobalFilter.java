package com.ecommerce.gateway.configs;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RateLimitGlobalFilter implements GlobalFilter, Ordered {
  private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>("""
      local current = redis.call('INCR', KEYS[1])
      if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
      return current
      """, Long.class);
  private final ReactiveStringRedisTemplate redis;
  private final ObjectMapper objectMapper;
  private final int publicLimit;
  private final int authenticatedLimit;
  private final int loginLimit;
  private final int checkoutLimit;
  private final long windowSeconds;

  public RateLimitGlobalFilter(
      ReactiveStringRedisTemplate redis, ObjectMapper objectMapper,
      @Value("${app.rate-limit.public-per-window:120}") int publicLimit,
      @Value("${app.rate-limit.authenticated-per-window:240}") int authenticatedLimit,
      @Value("${app.rate-limit.login-per-window:10}") int loginLimit,
      @Value("${app.rate-limit.checkout-per-window:30}") int checkoutLimit,
      @Value("${app.rate-limit.window-seconds:60}") long windowSeconds) {
    this.redis = redis;
    this.objectMapper = objectMapper;
    this.publicLimit = publicLimit;
    this.authenticatedLimit = authenticatedLimit;
    this.loginLimit = loginLimit;
    this.checkoutLimit = checkoutLimit;
    this.windowSeconds = windowSeconds;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getPath().value();
    if (path.equals("/actuator/health") || path.equals("/api/v1/payments/mock/callback")) {
      return chain.filter(exchange);
    }
    String userId = exchange.getAttribute("authenticatedUserId");
    String identity = userId == null ? "ip:" + clientIp(exchange) : "user:" + userId;
    int limit = limit(path, userId != null);
    long bucket = System.currentTimeMillis() / (windowSeconds * 1000L);
    String key = "gateway:rate:" + bucket + ":" + group(path) + ":" + identity;
    return redis.execute(SCRIPT, List.of(key), Long.toString(windowSeconds))
        .single(0L)
        .map(Optional::of)
        .onErrorReturn(Optional.empty())
        .flatMap(count -> {
          if (count.isEmpty()) {
            return allowWhenRedisUnavailable(exchange)
                ? chain.filter(exchange)
                : reject(exchange, HttpStatus.SERVICE_UNAVAILABLE, "RATE_LIMIT_UNAVAILABLE",
                    "Không thể kiểm tra giới hạn yêu cầu");
          }
          return count.get() > limit
              ? reject(exchange, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                  "Bạn đã gửi quá nhiều yêu cầu")
              : chain.filter(exchange);
        });
  }

  private boolean allowWhenRedisUnavailable(ServerWebExchange exchange) {
    return exchange.getRequest().getMethod() == HttpMethod.GET
        && exchange.getAttribute("authenticatedUserId") == null;
  }

  private int limit(String path, boolean authenticated) {
    if (path.equals("/api/v1/auth/login")) return loginLimit;
    if (path.equals("/api/v1/orders/checkout")) return checkoutLimit;
    return authenticated ? authenticatedLimit : publicLimit;
  }

  private String group(String path) {
    if (path.startsWith("/api/v1/auth/")) return "auth";
    if (path.startsWith("/api/v1/orders/checkout")) return "checkout";
    return "general";
  }

  private String clientIp(ServerWebExchange exchange) {
    InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
    return remote == null || remote.getAddress() == null
        ? "unknown" : remote.getAddress().getHostAddress();
  }

  private Mono<Void> reject(
      ServerWebExchange exchange, HttpStatus status, String code, String message) {
    exchange.getResponse().setStatusCode(status);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
    if (status == HttpStatus.TOO_MANY_REQUESTS) {
      exchange.getResponse().getHeaders().set("Retry-After", Long.toString(windowSeconds));
    }
    try {
      byte[] payload = objectMapper.writeValueAsBytes(
          new GatewayErrorResponse(
              code,
              message,
              null,
              exchange.getRequest().getHeaders().getFirst(CorrelationIdGlobalFilter.HEADER)));
      DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(payload);
      return exchange.getResponse().writeWith(Mono.just(buffer));
    } catch (Exception exception) {
      return exchange.getResponse().setComplete();
    }
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 1;
  }
}
