package com.ecommerce.identity.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
public class JwkController {

    private final KeyPair keyPair;

    public JwkController(KeyPair keyPair) {
        this.keyPair = keyPair;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> keys() {
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        return Map.of("keys", List.of(Map.of(
                "kty", "RSA",
                "kid", "ecommerce-identity",
                "use", "sig",
                "alg", "RS256",
                "n", encode(publicKey.getModulus()),
                "e", encode(publicKey.getPublicExponent())
        )));
    }

    private String encode(BigInteger value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray());
    }
}
