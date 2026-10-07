package com.ecommerce.gateway.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

  @Bean
  SecurityWebFilterChain securityWebFilterChain(
      ServerHttpSecurity http, JwtAuthenticationWebFilter jwtFilter) {
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .authorizeExchange(
            exchange ->
                exchange
                    .pathMatchers("/actuator/health")
                    .permitAll()
                    .pathMatchers(
                        HttpMethod.POST,
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh")
                    .permitAll()
                    .pathMatchers(
                        HttpMethod.GET,
                        "/api/v1/categories",
                        "/api/v1/categories/**",
                        "/api/v1/products",
                        "/api/v1/products/**")
                    .permitAll()
                    .pathMatchers("/api/v1/admin/**")
                    .hasAuthority("SCOPE_ADMIN")
                    .anyExchange()
                    .authenticated())
        .addFilterAt(jwtFilter, SecurityWebFiltersOrder.AUTHENTICATION)
        .build();
  }
}
