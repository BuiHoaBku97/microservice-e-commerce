package com.ecommerce.gateway.configs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.net.URI;
import java.security.KeyFactory;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthenticationWebFilter implements WebFilter {
    private final WebClient client;
    private final ObjectMapper objectMapper;
    private final URI jwkSetUri;

    public JwtAuthenticationWebFilter(WebClient.Builder builder, ObjectMapper objectMapper,
                                      @Value("${spring.security.jwt.jwk-set-uri}") URI jwkSetUri) {
        this.client = builder.build();
        this.objectMapper = objectMapper;
        this.jwkSetUri = jwkSetUri;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) return chain.filter(exchange);
        String token = header.substring(7);
        return client.get().uri(jwkSetUri).retrieve().bodyToMono(String.class)
                .map(body -> authenticate(token, body))
                .flatMap(authentication -> chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
                .onErrorResume(exception -> chain.filter(exchange));
    }

    private UsernamePasswordAuthenticationToken authenticate(String token, String jwkJson) {
        try {
            Claims claims = Jwts.parser().verifyWith(publicKey(jwkJson)).build().parseSignedClaims(token).getPayload();
            if (claims.getExpiration() == null || claims.getExpiration().before(new Date())) throw new IllegalArgumentException("JWT không hợp lệ");
            var authorities = Arrays.stream(claims.get("scope", String.class).split("\\s+"))
                    .filter(role -> !role.isBlank()).map(role -> new SimpleGrantedAuthority("SCOPE_" + role)).toList();
            return UsernamePasswordAuthenticationToken.authenticated(claims.getSubject(), token, authorities);
        } catch (Exception exception) { throw new IllegalArgumentException("JWT không hợp lệ", exception); }
    }

    private java.security.PublicKey publicKey(String body) throws Exception {
        JsonNode key = objectMapper.readTree(body).path("keys").get(0);
        byte[] modulus = java.util.Base64.getUrlDecoder().decode(key.path("n").asText());
        byte[] exponent = java.util.Base64.getUrlDecoder().decode(key.path("e").asText());
        return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(new java.math.BigInteger(1, modulus), new java.math.BigInteger(1, exponent)));
    }
}
