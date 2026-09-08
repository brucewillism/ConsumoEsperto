package com.consumoesperto.controller;

import com.consumoesperto.eco.EcoException;
import com.consumoesperto.edith.CapabilityManifestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Manifesto de capabilities — segredo de serviço, não sessão de usuário.
 */
@RestController
@RequiredArgsConstructor
public class CapabilityManifestController {

    public static final String SERVICE_KEY = "X-Eco-Service-Key";

    private final CapabilityManifestService manifestService;

    @GetMapping({"/capabilities", "/api/capabilities"})
    public ResponseEntity<Map<String, Object>> get(
        @RequestHeader(value = SERVICE_KEY, required = false) String serviceKey,
        @RequestHeader(value = "X-API-Key", required = false) String apiKey
    ) {
        String provided = serviceKey != null && !serviceKey.isBlank() ? serviceKey : apiKey;
        if (!manifestService.serviceSecretMatches(provided)) {
            throw EcoException.unauthorized("segredo de serviço inválido");
        }
        return ResponseEntity.ok(manifestService.manifest());
    }
}
