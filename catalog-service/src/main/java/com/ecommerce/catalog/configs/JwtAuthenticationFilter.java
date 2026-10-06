package com.ecommerce.catalog.configs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.security.KeyFactory;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final URI jwkSetUri;
    public JwtAuthenticationFilter(RestClient.Builder builder, ObjectMapper objectMapper, @Value("${spring.security.jwt.jwk-set-uri}") URI jwkSetUri) { this.client = builder.build(); this.objectMapper = objectMapper; this.jwkSetUri = jwkSetUri; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            try {
                String token = header.substring(7);
                Claims claims = Jwts.parser().verifyWith(publicKey(client.get().uri(jwkSetUri).retrieve().body(String.class))).build().parseSignedClaims(token).getPayload();
                if (claims.getExpiration() == null || claims.getExpiration().before(new Date())) throw new IllegalArgumentException();
                var authorities = Arrays.stream(claims.get("scope", String.class).split("\\s+")).filter(role -> !role.isBlank()).map(role -> new SimpleGrantedAuthority("SCOPE_" + role)).toList();
                SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(claims.getSubject(), token, authorities));
            } catch (Exception ignored) { SecurityContextHolder.clearContext(); }
        }
        chain.doFilter(request, response);
    }

    private java.security.PublicKey publicKey(String body) throws Exception {
        JsonNode key = objectMapper.readTree(body).path("keys").get(0);
        byte[] modulus = java.util.Base64.getUrlDecoder().decode(key.path("n").asText());
        byte[] exponent = java.util.Base64.getUrlDecoder().decode(key.path("e").asText());
        return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(new java.math.BigInteger(1, modulus), new java.math.BigInteger(1, exponent)));
    }
}
