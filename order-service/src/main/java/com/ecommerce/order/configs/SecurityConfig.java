package com.ecommerce.order.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtAuthenticationFilter jwtFilter,
      org.springframework.beans.factory.ObjectProvider<ActiveAccountFilter> activeAccountFilter)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health", "/actuator/prometheus")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/inventory/availability")
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**")
                    .hasAuthority("SCOPE_ADMIN")
                    .requestMatchers("/api/v1/cart/**", "/api/v1/orders/**")
                    .hasAuthority("SCOPE_CUSTOMER")
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
                .class);
    ActiveAccountFilter filter = activeAccountFilter.getIfAvailable();
    if (filter != null) {
      http.addFilterAfter(filter, JwtAuthenticationFilter.class);
    }
    return http.build();
  }
}
