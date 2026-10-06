package com.ecommerce.identity.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.beans.factory.annotation.Value;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**", "/.well-known/jwks.json", "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    KeyPair jwtKeyPair(
            @Value("${security.jwt.private-key-path:./secrets/jwt-private-key.pem}") String privateKeyPath,
            @Value("${security.jwt.public-key-path:./secrets/jwt-public-key.pem}") String publicKeyPath) throws Exception {
        PrivateKey privateKey = readPrivateKey(resolveSecretPath(privateKeyPath));
        PublicKey publicKey = readPublicKey(resolveSecretPath(publicKeyPath));
        return new KeyPair(publicKey, privateKey);
    }

    private Path resolveSecretPath(String configuredPath) {
        Path path = Path.of(configuredPath);
        if (Files.exists(path)) return path;
        Path parentProjectPath = Path.of("..").resolve(path).normalize();
        if (Files.exists(parentProjectPath)) return parentProjectPath;
        return path;
    }

    private PrivateKey readPrivateKey(Path path) throws Exception {
        return KeyFactory.getInstance("RSA").generatePrivate(
                new PKCS8EncodedKeySpec(decodePem(Files.readString(path), "PRIVATE KEY")));
    }

    private PublicKey readPublicKey(Path path) throws Exception {
        return KeyFactory.getInstance("RSA").generatePublic(
                new X509EncodedKeySpec(decodePem(Files.readString(path), "PUBLIC KEY")));
    }

    private byte[] decodePem(String pem, String type) {
        String body = pem.replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }

}
