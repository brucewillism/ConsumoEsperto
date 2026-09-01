package com.consumoesperto.edith;

import com.consumoesperto.config.EdithProperties;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Gera e valida {@code context_ref} opaco — nunca expor userId como autoridade em callbacks.
 */
@Service
public class EdithContextRefService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final EdithProperties properties;

    public EdithContextRefService(EdithProperties properties) {
        this.properties = properties;
    }

    public String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String scopeLabel() {
        return properties.getProject();
    }
}
