package com.ecommerce.payment.configs;

import com.ecommerce.payment.models.dtos.responses.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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
      ActiveAccountFilter activeAccountFilter,
      ObjectMapper objectMapper)
      throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health", "/actuator/prometheus")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/payments/mock/callback")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/payments/vnpay/ipn", "/api/v1/payments/vnpay/return")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/orders/*/payment")
                    .hasAuthority("SCOPE_CUSTOMER")
                    .requestMatchers(HttpMethod.POST, "/api/v1/orders/*/payment/initialize")
                    .hasAuthority("SCOPE_CUSTOMER")
                    .requestMatchers("/api/v1/admin/**")
                    .hasAuthority("SCOPE_ADMIN")
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(
                        (request, response, exception) -> {
                          response.setStatus(HttpStatus.UNAUTHORIZED.value());
                          response.setContentType("application/json;charset=UTF-8");
                          objectMapper.writeValue(
                              response.getWriter(),
                              ApiErrorResponse.of(
                                  "UNAUTHENTICATED",
                                  "Yêu cầu xác thực không hợp lệ hoặc JWT đã hết hạn",
                                  request.getRequestId()));
                        })
                    .accessDeniedHandler(
                        (request, response, exception) -> {
                          response.setStatus(HttpStatus.FORBIDDEN.value());
                          response.setContentType("application/json;charset=UTF-8");
                          objectMapper.writeValue(
                              response.getWriter(),
                              ApiErrorResponse.of(
                                  "FORBIDDEN",
                                  "Bạn không có quyền truy cập chức năng này",
                                  request.getRequestId()));
                        }))
        .addFilterBefore(
            jwtFilter,
            org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
                .class)
        .addFilterAfter(activeAccountFilter, JwtAuthenticationFilter.class)
        .build();
  }
}
