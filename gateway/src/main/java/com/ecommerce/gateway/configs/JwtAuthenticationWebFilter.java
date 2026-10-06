package com.ecommerce.gateway.configs;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.security.interfaces.RSAPublicKey;
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
    private final URI jwkSetUri;

    public JwtAuthenticationWebFilter(WebClient.Builder builder,
                                      @Value("${spring.security.jwt.jwk-set-uri}") URI jwkSetUri) {
        this.client = builder.build();
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
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) throw new IllegalArgumentException("Thuật toán JWT không hợp lệ");
            RSAKey key = JWKSet.parse(jwkJson).getKeyByKeyId(jwt.getHeader().getKeyID()).toRSAKey();
            if (!jwt.verify(new RSASSAVerifier((RSAPublicKey) key.toPublicKey())) || jwt.getJWTClaimsSet().getExpirationTime().before(new Date())) throw new IllegalArgumentException("JWT không hợp lệ");
            var authorities = Arrays.stream(jwt.getJWTClaimsSet().getStringClaim("scope").split("\\s+"))
                    .filter(role -> !role.isBlank()).map(role -> new SimpleGrantedAuthority("SCOPE_" + role)).toList();
            return UsernamePasswordAuthenticationToken.authenticated(jwt.getJWTClaimsSet().getSubject(), token, authorities);
        } catch (Exception exception) { throw new IllegalArgumentException("JWT không hợp lệ", exception); }
    }
}
