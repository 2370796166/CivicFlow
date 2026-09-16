package com.civicflow.auth.controller;

import com.civicflow.auth.config.JwtKeyManager;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JwkSetController {
    private final JwtKeyManager keyManager;

    public JwkSetController(JwtKeyManager keyManager) {
        this.keyManager = keyManager;
    }

    @GetMapping("/.well-known/jwks.json")
    Map<String, Object> jwks() {
        return keyManager.publicJwkSet();
    }
}
