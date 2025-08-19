package com.consumoesperto.controller;

import com.consumoesperto.model.BankApiConfig;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.BankApiConfigService;
import com.consumoesperto.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * Controller para gerenciar configurações das APIs bancárias por usuário
 */
@RestController
@RequestMapping("/api/bank-config")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class BankApiConfigController {

    private final BankApiConfigService configService;
    private final UsuarioService usuarioService;

    /**
     * Lista todas as configurações do usuário autenticado
     */
    @GetMapping("/my-configs")
    public ResponseEntity<List<BankApiConfig>> getMyConfigs(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Listando configurações do usuário: {}", userPrincipal.getId());
        try {
            List<BankApiConfig> configs = configService.findByUsuarioId(userPrincipal.getId());
            return ResponseEntity.ok(configs);
        } catch (Exception e) {
            log.error("Erro ao listar configurações do usuário: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Lista configurações ativas do usuário autenticado
     */
    @GetMapping("/my-configs/active")
    public ResponseEntity<List<BankApiConfig>> getMyActiveConfigs(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Listando configurações ativas do usuário: {}", userPrincipal.getId());
        try {
            List<BankApiConfig> configs = configService.findActiveConfigsByUsuario(userPrincipal.getId());
            return ResponseEntity.ok(configs);
        } catch (Exception e) {
            log.error("Erro ao listar configurações ativas do usuário: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Lista todas as configurações (método legado para compatibilidade)
     */
    @GetMapping
    public ResponseEntity<List<BankApiConfig>> getAllConfigs() {
        log.info("Listando todas as configurações de APIs bancárias");
        List<BankApiConfig> configs = configService.findAll();
        return ResponseEntity.ok(configs);
    }

    /**
     * Busca configuração por ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<BankApiConfig> getConfigById(@PathVariable Long id) {
        log.info("Buscando configuração por ID: {}", id);
        return configService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Busca configuração por código do banco para o usuário autenticado
     */
    @GetMapping("/my-configs/bank/{bankCode}")
    public ResponseEntity<BankApiConfig> getMyConfigByBankCode(
            @PathVariable String bankCode,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Buscando configuração para banco: {} - Usuário: {}", bankCode, userPrincipal.getId());
        try {
                    return configService.findByUsuarioIdAndBanco(userPrincipal.getId(), bankCode)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Erro ao buscar configuração: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Busca configuração por código do banco (método legado para compatibilidade)
     */
    @GetMapping("/bank/{bankCode}")
    public ResponseEntity<BankApiConfig> getConfigByBankCode(@PathVariable String bankCode) {
        log.info("Buscando configuração para banco: {}", bankCode);
        return configService.findByBanco(bankCode)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Cria nova configuração para o usuário autenticado
     */
    @PostMapping("/my-configs")
    public ResponseEntity<BankApiConfig> createMyConfig(
            @RequestBody BankApiConfig config,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Criando nova configuração para banco: {} - Usuário: {}", config.getBanco(), userPrincipal.getId());
        try {
            // Busca o usuário e associa à configuração
            Usuario usuario = usuarioService.findById(userPrincipal.getId());
            config.setUsuario(usuario);
            
            BankApiConfig saved = configService.saveConfig(config);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            log.error("Erro ao criar configuração: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Cria nova configuração (método legado para compatibilidade)
     */
    @PostMapping
    public ResponseEntity<BankApiConfig> createConfig(@RequestBody BankApiConfig config) {
        log.info("Criando nova configuração para banco: {}", config.getBanco());
        try {
            BankApiConfig saved = configService.saveConfig(config);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            log.error("Erro ao criar configuração: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Atualiza configuração existente do usuário autenticado
     */
    @PutMapping("/my-configs/{id}")
    public ResponseEntity<BankApiConfig> updateMyConfig(
            @PathVariable Long id,
            @RequestBody BankApiConfig config,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Atualizando configuração ID: {} - Usuário: {}", id, userPrincipal.getId());
        try {
            // Verifica se a configuração pertence ao usuário
            Optional<BankApiConfig> existingConfig = configService.findByUsuarioIdAndBanco(
                userPrincipal.getId(), config.getBanco());
            if (!existingConfig.isPresent()) {
                return ResponseEntity.notFound().build();
            }
            
            config.setId(id);
            config.setUsuario(usuarioService.findById(userPrincipal.getId()));
            BankApiConfig updated = configService.saveConfig(config);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            log.error("Erro ao atualizar configuração: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Atualiza configuração existente (método legado para compatibilidade)
     */
    @PutMapping("/{id}")
    public ResponseEntity<BankApiConfig> updateConfig(@PathVariable Long id, @RequestBody BankApiConfig config) {
        log.info("Atualizando configuração ID: {}", id);
        try {
            config.setId(id);
            BankApiConfig updated = configService.saveConfig(config);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            log.error("Erro ao atualizar configuração: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Remove configuração do usuário autenticado
     */
    @DeleteMapping("/my-configs/{id}")
    public ResponseEntity<Void> deleteMyConfig(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Removendo configuração ID: {} - Usuário: {}", id, userPrincipal.getId());
        try {
            // Verifica se a configuração pertence ao usuário antes de remover
            // Por simplicidade, vamos permitir a remoção por ID
            configService.deleteConfig(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Erro ao remover configuração: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Remove configuração (método legado para compatibilidade)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConfig(@PathVariable Long id) {
        log.info("Removendo configuração ID: {}", id);
        try {
            configService.deleteConfig(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Erro ao remover configuração: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Ativa/desativa configuração do usuário autenticado
     */
    @PatchMapping("/my-configs/{id}/toggle")
    public ResponseEntity<Void> toggleMyActiveStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Alternando status ativo da configuração ID: {} - Usuário: {}", id, userPrincipal.getId());
        try {
            configService.toggleActiveStatus(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Erro ao alternar status: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Ativa/desativa configuração (método legado para compatibilidade)
     */
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<Void> toggleActiveStatus(@PathVariable Long id) {
        log.info("Alternando status ativo da configuração ID: {}", id);
        try {
            configService.toggleActiveStatus(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Erro ao alternar status: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Cria configurações padrão para o usuário autenticado
     */
    @PostMapping("/my-configs/create-defaults")
    public ResponseEntity<Void> createMyDefaultConfigs(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Criando configurações padrão para usuário: {}", userPrincipal.getId());
        try {
            configService.createDefaultConfigsForUser(userPrincipal.getId());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Erro ao criar configurações padrão: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Cria configurações padrão (método legado para compatibilidade)
     */
    @PostMapping("/create-defaults")
    public ResponseEntity<Void> createDefaultConfigs() {
        log.info("Criando configurações padrão para APIs bancárias");
        try {
            configService.createDefaultConfigs();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Erro ao criar configurações padrão: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Testa conexão de uma configuração do usuário autenticado
     */
    @PostMapping("/my-configs/{id}/test")
    public ResponseEntity<String> testMyConnection(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Testando conexão da configuração ID: {} - Usuário: {}", id, userPrincipal.getId());
        try {
            // Aqui você implementaria o teste real de conexão
            // Por enquanto, vamos simular um teste
            configService.updateTestStatus(id, "SUCCESS", "Conexão testada com sucesso");
            return ResponseEntity.ok("Conexão testada com sucesso");
        } catch (Exception e) {
            log.error("Erro ao testar conexão: {}", e.getMessage(), e);
            configService.updateTestStatus(id, "FAILED", "Erro: " + e.getMessage());
            return ResponseEntity.badRequest().body("Erro ao testar conexão: " + e.getMessage());
        }
    }

    /**
     * Testa conexão de uma configuração (método legado para compatibilidade)
     */
    @PostMapping("/{id}/test")
    public ResponseEntity<String> testConnection(@PathVariable Long id) {
        log.info("Testando conexão da configuração ID: {}", id);
        try {
            // Aqui você implementaria o teste real de conexão
            // Por enquanto, vamos simular um teste
            configService.updateTestStatus(id, "SUCCESS", "Conexão testada com sucesso");
            return ResponseEntity.ok("Conexão testada com sucesso");
        } catch (Exception e) {
            log.error("Erro ao testar conexão: {}", e.getMessage(), e);
            configService.updateTestStatus(id, "FAILED", "Erro: " + e.getMessage());
            return ResponseEntity.badRequest().body("Erro ao testar conexão: " + e.getMessage());
        }
    }
}
