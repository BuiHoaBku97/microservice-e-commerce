package com.ecommerce.notification.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtAuthenticationFilter jwtFilter, ActiveAccountFilter activeAccountFilter)
      throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health")
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**")
                    .hasAuthority("SCOPE_ADMIN")
                    .requestMatchers("/api/v1/notifications/**")
                    .hasAnyAuthority("SCOPE_CUSTOMER", "SCOPE_ADMIN")
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
        .build();
  }
}
