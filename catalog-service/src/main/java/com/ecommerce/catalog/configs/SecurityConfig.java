package com.ecommerce.catalog.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtAuthenticationFilter jwtFilter,
      InternalServiceTokenFilter internalServiceTokenFilter,
      ActiveAccountFilter activeAccountFilter)
      throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health", "/actuator/prometheus")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST, "/internal/v1/skus/checkout-items")
                    .hasAuthority("SCOPE_catalog.checkout")
                    .requestMatchers(HttpMethod.GET, "/internal/v1/skus/{skuId}")
                    .hasAuthority("SCOPE_catalog.read")
                    .requestMatchers("/internal/**")
                    .denyAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/categories", "/api/v1/products/**")
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**")
                    .hasAuthority("SCOPE_ADMIN")
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(exceptions -> exceptions
            .authenticationEntryPoint((request, response, exception) ->
                JsonErrorWriter.write(response, 401, "UNAUTHENTICATED", request.getRequestId()))
            .accessDeniedHandler((request, response, exception) ->
                JsonErrorWriter.write(response, 403, "FORBIDDEN", request.getRequestId())))
        .addFilterBefore(
            jwtFilter,
            org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
                .class)
        .addFilterAfter(activeAccountFilter, JwtAuthenticationFilter.class)
        .addFilterAfter(internalServiceTokenFilter, ActiveAccountFilter.class)
        .build();
  }
}
