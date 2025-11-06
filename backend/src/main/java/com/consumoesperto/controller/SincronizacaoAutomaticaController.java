package com.consumoesperto.controller;

import com.consumoesperto.service.AutoTokenSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/sincronizacao")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:4200", "https://0d723f1e294f.ngrok-free.app"})
public class SincronizacaoAutomaticaController {

    private final AutoTokenSyncService autoTokenSyncService;

    @PostMapping("/forcar/{userId}")
    public ResponseEntity<Map<String, Object>> forcarSincronizacao(@PathVariable Long userId) {
        log.info("🔄 FORÇANDO SINCRONIZAÇÃO AUTOMÁTICA para usuário: {}", userId);
        try {
            autoTokenSyncService.verificarESincronizarTokens(userId);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Sincronização automática executada com sucesso",
                "userId", userId
            ));
        } catch (Exception e) {
            log.error("❌ Erro na sincronização automática para usuário {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "status", "error",
                "message", "Erro na sincronização: " + e.getMessage(),
                "userId", userId
            ));
        }
    }
}
