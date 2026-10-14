package com.ecommerce.order.configs;

import io.jsonwebtoken.Jwts;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ServiceJwtProvider {
  private final PrivateKey privateKey;
  private final String issuer;
  private final String subject;
  private final String keyId;
  private final long ttlSeconds;

  public ServiceJwtProvider(
      @Value("${app.service-jwt.private-key-base64:${spring.security.jwt.private-key-base64}}")
          String privateKeyBase64,
      @Value("${app.service-jwt.issuer:ecommerce-internal}") String issuer,
      @Value("${app.service-jwt.subject:order-service}") String subject,
      @Value("${app.service-jwt.key-id:service-key-1}") String keyId,
      @Value("${app.service-jwt.ttl-seconds:60}") long ttlSeconds)
      throws Exception {
    this.privateKey =
        KeyFactory.getInstance("RSA")
            .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyBase64)));
    this.issuer = issuer;
    this.subject = subject;
    this.keyId = keyId;
    this.ttlSeconds = ttlSeconds;
  }

  public String issue(String audience, String scope) {
    Instant now = Instant.now();
    return Jwts.builder()
        .header()
        .keyId(keyId)
        .and()
        .issuer(issuer)
        .subject(subject)
        .audience()
        .add(audience)
        .and()
        .claim("scope", scope)
        .id(UUID.randomUUID().toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(ttlSeconds)))
        .signWith(privateKey, Jwts.SIG.RS256)
        .compact();
  }
}
