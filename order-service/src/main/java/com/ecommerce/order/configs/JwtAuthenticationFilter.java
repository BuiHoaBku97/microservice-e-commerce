package com.ecommerce.order.configs;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.security.interfaces.RSAPublicKey;
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
    private final RestClient client; private final URI jwkSetUri;
    public JwtAuthenticationFilter(RestClient.Builder builder, @Value("${spring.security.jwt.jwk-set-uri}") URI jwkSetUri) { this.client = builder.build(); this.jwkSetUri = jwkSetUri; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            try {
                SignedJWT jwt = SignedJWT.parse(header.substring(7));
                if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) throw new IllegalArgumentException();
                RSAKey key = JWKSet.parse(client.get().uri(jwkSetUri).retrieve().body(String.class)).getKeyByKeyId(jwt.getHeader().getKeyID()).toRSAKey();
                if (!jwt.verify(new RSASSAVerifier((RSAPublicKey) key.toPublicKey())) || jwt.getJWTClaimsSet().getExpirationTime().before(new Date())) throw new IllegalArgumentException();
                var authorities = Arrays.stream(jwt.getJWTClaimsSet().getStringClaim("scope").split("\\s+")).filter(role -> !role.isBlank()).map(role -> new SimpleGrantedAuthority("SCOPE_" + role)).toList();
                SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(jwt.getJWTClaimsSet().getSubject(), header.substring(7), authorities));
            } catch (Exception ignored) { SecurityContextHolder.clearContext(); }
        }
        chain.doFilter(request, response);
    }
}
