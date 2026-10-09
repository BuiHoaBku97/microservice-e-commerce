package com.ecommerce.payment.configs;

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
  SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter)
      throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/payments/**")
                    .authenticated()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            exceptions ->
                exceptions.authenticationEntryPoint(
                    (request, response, exception) -> {
                      response.setStatus(HttpStatus.UNAUTHORIZED.value());
                      response.setContentType("application/json;charset=UTF-8");
                      response.getWriter().write(
                          "{\"message\":\"Yêu cầu xác thực không hợp lệ hoặc JWT đã hết hạn\"}");
                    }))
        .addFilterBefore(
            jwtFilter,
            org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
                .class)
        .build();
  }
}
