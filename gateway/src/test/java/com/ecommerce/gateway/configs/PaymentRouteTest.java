package com.ecommerce.gateway.configs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.handler.predicate.MethodRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class PaymentRouteTest {
  @Test
  void paymentQueryWinsOverOrderWildcardAndRemainsProtected() throws Exception {
    var routes = routes();
    var request =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders/10/payment"));
    var matching = routes.stream().filter(route -> matches(route, request)).toList();
    assertThat(matching.getFirst().getId()).isEqualTo("payment-by-order");
    assertThat(matching.getFirst().getUri().toString()).isEqualTo("lb://payment-service");
    assertThat(new RouteValidator().isOpenApi(request)).isFalse();
    var post = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/orders/10/payment"));
    assertThat(matches(matching.getFirst(), post)).isFalse();
  }

  @Test
  void internalOrderApiIsNeverForwardedToAnyService() throws Exception {
    var request =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/internal/v1/orders/10/payment-context"));
    assertThat(
            routes().stream().filter(route -> matches(route, request)).map(RouteDefinition::getId))
        .containsExactly("unmatched-route");
  }

  private List<RouteDefinition> routes() throws Exception {
    var environment = new StandardEnvironment();
    new YamlPropertySourceLoader()
        .load("gateway", new ClassPathResource("application.yml"))
        .forEach(source -> environment.getPropertySources().addLast(source));
    return Binder.get(environment)
        .bind("spring.cloud.gateway", GatewayProperties.class)
        .get()
        .getRoutes()
        .stream()
        .sorted(Comparator.comparingInt(RouteDefinition::getOrder))
        .toList();
  }

  private boolean matches(RouteDefinition route, MockServerWebExchange exchange) {
    return route.getPredicates().stream()
        .allMatch(
            predicate -> {
              if (predicate.getName().equals("Path")) {
                var config = new PathRoutePredicateFactory.Config();
                config.setPatterns(
                    predicate.getArgs().values().stream()
                        .flatMap(value -> Arrays.stream(value.split(",")))
                        .toList());
                return new PathRoutePredicateFactory().apply(config).test(exchange);
              }
              if (predicate.getName().equals("Method")) {
                var config = new MethodRoutePredicateFactory.Config();
                config.setMethods(
                    predicate.getArgs().values().stream()
                        .map(HttpMethod::valueOf)
                        .toArray(HttpMethod[]::new));
                return new MethodRoutePredicateFactory().apply(config).test(exchange);
              }
              throw new AssertionError("Chưa hỗ trợ predicate: " + predicate.getName());
            });
  }
}
