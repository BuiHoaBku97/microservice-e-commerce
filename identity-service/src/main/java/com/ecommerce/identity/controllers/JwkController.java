package com.ecommerce.identity.controllers;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;

@RestController
public class JwkController {

    private final KeyPair keyPair;

    public JwkController(KeyPair keyPair) {
        this.keyPair = keyPair;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> keys() {
        RSAKey publicKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .keyID("ecommerce-identity")
                .build();
        return new JWKSet(publicKey).toJSONObject();
    }
}
