package com.consumoesperto.controller;

import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.DynamicBankConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller para fornecer estatísticas das configurações das APIs bancárias por usuário
 */
@RestController
@RequestMapping("/api/bank-config/stats")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:4200", "https://22e294954ab2.ngrok-free.app"})
public class BankConfigStatsController {

    private final DynamicBankConfigService dynamicConfigService;

    /**
     * Obtém estatísticas das configurações do usuário autenticado
     */
    @GetMapping("/my-stats")
    public ResponseEntity<Map<String, Object>> getMyStats(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Obtendo estatísticas das configurações bancárias do usuário: {}", userPrincipal.getId());
        try {
            Map<String, Object> stats = dynamicConfigService.getConfigStatsForUser(userPrincipal.getId());
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Erro ao obter estatísticas do usuário: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Obtém lista de bancos configurados para o usuário autenticado
     */
    @GetMapping("/my-banks")
    public ResponseEntity<java.util.Set<String>> getMyConfiguredBanks(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Obtendo lista de bancos configurados para usuário: {}", userPrincipal.getId());
        try {
            java.util.Set<String> banks = dynamicConfigService.getConfiguredBanksForUser(userPrincipal.getId());
            return ResponseEntity.ok(banks);
        } catch (Exception e) {
            log.error("Erro ao obter bancos configurados do usuário: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Verifica se há configurações carregadas para o usuário autenticado
     */
    @GetMapping("/my-configs/has-configs")
    public ResponseEntity<Boolean> hasMyConfigs(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Verificando se há configurações carregadas para usuário: {}", userPrincipal.getId());
        try {
            boolean hasConfigs = dynamicConfigService.hasConfigsForUser(userPrincipal.getId());
            return ResponseEntity.ok(hasConfigs);
        } catch (Exception e) {
            log.error("Erro ao verificar configurações do usuário: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Recarrega todas as configurações do usuário autenticado
     */
    @PostMapping("/my-configs/reload")
    public ResponseEntity<String> reloadMyConfigs(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Recarregando configurações bancárias do usuário: {}", userPrincipal.getId());
        try {
            // Recarrega configurações do usuário específico
            dynamicConfigService.loadUserConfigs(userPrincipal.getId());
            return ResponseEntity.ok("Configurações recarregadas com sucesso");
        } catch (Exception e) {
            log.error("Erro ao recarregar configurações do usuário: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Erro ao recarregar configurações: " + e.getMessage());
        }
    }

    /**
     * Recarrega configuração específica do usuário autenticado
     */
    @PostMapping("/my-configs/reload/{bankCode}")
    public ResponseEntity<String> reloadMyConfig(
            @PathVariable String bankCode,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Recarregando configuração para banco: {} - usuário: {}", bankCode, userPrincipal.getId());
        try {
            dynamicConfigService.reloadUserConfig(userPrincipal.getId(), bankCode);
            return ResponseEntity.ok("Configuração recarregada com sucesso");
        } catch (Exception e) {
            log.error("Erro ao recarregar configuração {} do usuário {}: {}", bankCode, userPrincipal.getId(), e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Erro ao recarregar configuração: " + e.getMessage());
        }
    }

    /**
     * Obtém estatísticas gerais das configurações (método legado para compatibilidade)
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getStats() {
        log.info("Obtendo estatísticas gerais das configurações bancárias");
        try {
            Map<String, Object> stats = dynamicConfigService.getConfigStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Erro ao obter estatísticas gerais: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Obtém lista de bancos configurados (método legado para compatibilidade)
     */
    @GetMapping("/banks")
    public ResponseEntity<java.util.Set<String>> getConfiguredBanks() {
        log.info("Obtendo lista geral de bancos configurados");
        try {
            java.util.Set<String> banks = dynamicConfigService.getConfiguredBanks();
            return ResponseEntity.ok(banks);
        } catch (Exception e) {
            log.error("Erro ao obter bancos configurados gerais: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Verifica se há configurações carregadas (método legado para compatibilidade)
     */
    @GetMapping("/has-configs")
    public ResponseEntity<Boolean> hasConfigs() {
        log.info("Verificando se há configurações carregadas");
        try {
            boolean hasConfigs = dynamicConfigService.hasConfigs();
            return ResponseEntity.ok(hasConfigs);
        } catch (Exception e) {
            log.error("Erro ao verificar configurações gerais: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Recarrega todas as configurações (método legado para compatibilidade)
     */
    @PostMapping("/reload")
    public ResponseEntity<String> reloadAllConfigs() {
        log.info("Recarregando todas as configurações bancárias");
        try {
            dynamicConfigService.loadAllConfigs();
            return ResponseEntity.ok("Configurações recarregadas com sucesso");
        } catch (Exception e) {
            log.error("Erro ao recarregar configurações gerais: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Erro ao recarregar configurações: " + e.getMessage());
        }
    }

    /**
     * Recarrega configuração específica (método legado para compatibilidade)
     */
    @PostMapping("/reload/{bankCode}")
    public ResponseEntity<String> reloadConfig(@PathVariable String bankCode) {
        log.info("Recarregando configuração para banco: {}", bankCode);
        try {
            dynamicConfigService.reloadConfig(bankCode);
            return ResponseEntity.ok("Configuração recarregada com sucesso");
        } catch (Exception e) {
            log.error("Erro ao recarregar configuração {}: {}", bankCode, e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Erro ao recarregar configuração: " + e.getMessage());
        }
    }
}
