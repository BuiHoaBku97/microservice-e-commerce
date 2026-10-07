package com.ecommerce.gateway.configs;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
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
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthenticationWebFilter implements WebFilter {
    private final PublicKey publicKey;

    public JwtAuthenticationWebFilter(@Value("${spring.security.jwt.public-key-base64}") String publicKeyBase64) throws Exception {
        this.publicKey = KeyFactory.getInstance("RSA").generatePublic(
                new X509EncodedKeySpec(java.util.Base64.getDecoder().decode(publicKeyBase64)));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) return chain.filter(exchange);
        String token = header.substring(7);
        try {
            var authentication = authenticate(token);
            return chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
        } catch (Exception exception) {
            return chain.filter(exchange);
        }
    }

    private UsernamePasswordAuthenticationToken authenticate(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token).getPayload();
            if (claims.getExpiration() == null || claims.getExpiration().before(new Date())) throw new IllegalArgumentException("JWT không hợp lệ");
            var authorities = Arrays.stream(claims.get("scope", String.class).split("\\s+"))
                    .filter(role -> !role.isBlank()).map(role -> new SimpleGrantedAuthority("SCOPE_" + role)).toList();
            return UsernamePasswordAuthenticationToken.authenticated(claims.getSubject(), token, authorities);
        } catch (Exception exception) { throw new IllegalArgumentException("JWT không hợp lệ", exception); }
    }

}
