package com.ecommerce.identity.configs;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
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
                auth.requestMatchers("/api/v1/auth/**", "/actuator/health", "/actuator/prometheus")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST, "/internal/v1/users/current/checkout-context")
                    .hasAuthority("SCOPE_identity.checkout")
                    .requestMatchers(HttpMethod.GET, "/internal/v1/users/*/status")
                    .hasAuthority("SCOPE_identity.status")
                    .requestMatchers("/internal/**")
                    .denyAll()
                    .requestMatchers("/api/v1/admin/**")
                    .hasAuthority("SCOPE_ADMIN")
                    .requestMatchers("/api/v1/users/**")
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
        .addFilterAfter(internalServiceTokenFilter, JwtAuthenticationFilter.class)
        .build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  KeyPair jwtKeyPair(
      @Value("${security.jwt.private-key-base64}") String privateKeyBase64,
      @Value("${security.jwt.public-key-base64}") String publicKeyBase64)
      throws Exception {
    PrivateKey privateKey = readPrivateKey(privateKeyBase64);
    PublicKey publicKey = readPublicKey(publicKeyBase64);
    return new KeyPair(publicKey, privateKey);
  }

  private PrivateKey readPrivateKey(String keyBase64) throws Exception {
    return KeyFactory.getInstance("RSA")
        .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(keyBase64)));
  }

  private PublicKey readPublicKey(String keyBase64) throws Exception {
    return KeyFactory.getInstance("RSA")
        .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(keyBase64)));
  }
}
