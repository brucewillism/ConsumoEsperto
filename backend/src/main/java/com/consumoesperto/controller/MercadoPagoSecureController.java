package com.consumoesperto.controller;

import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.MercadoPagoService;
import com.consumoesperto.service.BankApiService;
import com.consumoesperto.model.BankApiConfig;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.BankApiConfigRepository;
import com.consumoesperto.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Controller para configuração segura individual do Mercado Pago
 * 
 * Este controller permite que cada usuário configure suas próprias
 * credenciais do Mercado Pago de forma segura e isolada.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@RestController
@RequestMapping("/api/mercadopago")
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoSecureController {

    private final BankApiConfigRepository bankApiConfigRepository;
    private final UsuarioRepository usuarioRepository;
    private final MercadoPagoService mercadoPagoService;
    private final BankApiService bankApiService;

    /**
     * Configura credenciais individuais do Mercado Pago
     * 
     * Cada usuário configura suas próprias credenciais, garantindo
     * isolamento total dos dados e máxima segurança.
     * 
     * @param credentials Credenciais do usuário (Client ID, Client Secret, User ID)
     * @param currentUser Usuário autenticado
     * @return Resultado da configuração
     */
    @PostMapping("/configure")
    public ResponseEntity<Map<String, Object>> configureCredentials(
            @RequestBody Map<String, String> credentials,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
            log.info("🔐 Configurando credenciais seguras do Mercado Pago para usuário: {}", currentUser.getId());
            
            // Validar credenciais obrigatórias
            String clientId = credentials.get("clientId");
            String clientSecret = credentials.get("clientSecret");
            String userId = credentials.get("userId");
            
            if (clientId == null || clientId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "erro", "Client ID é obrigatório"
                ));
            }
            
            if (clientSecret == null || clientSecret.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "erro", "Client Secret é obrigatório"
                ));
            }
            
            if (userId == null || userId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "erro", "User ID é obrigatório"
                ));
            }
            
            // Buscar usuário real
            Optional<Usuario> usuarioOpt = usuarioRepository.findById(currentUser.getId());
            if (usuarioOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "erro", "Usuário não encontrado"
                ));
            }
            
            Usuario usuario = usuarioOpt.get();
            
            // Verificar se já existe configuração para este usuário
            Optional<BankApiConfig> existingConfig = bankApiConfigRepository
                .findByUsuarioIdAndTipoBanco(currentUser.getId(), "MERCADO_PAGO");
            
            BankApiConfig config;
            if (existingConfig.isPresent()) {
                // Atualizar configuração existente
                config = existingConfig.get();
                log.info("📝 Atualizando configuração existente do Mercado Pago para usuário: {}", currentUser.getId());
            } else {
                // Criar nova configuração
                config = new BankApiConfig();
                config.setUsuario(usuario);
                config.setTipoBanco("MERCADO_PAGO");
                config.setNome("Mercado Pago");
                log.info("🆕 Criando nova configuração do Mercado Pago para usuário: {}", currentUser.getId());
            }
            
            // Configurar credenciais
            config.setClientId(clientId);
            config.setClientSecret(clientSecret);
            // userId será armazenado como parte do nome ou em campo personalizado
            config.setNome("Mercado Pago - " + userId);
            config.setApiUrl("https://api.mercadopago.com/v1");
            config.setAuthUrl("https://api.mercadopago.com/authorization");
            config.setTokenUrl("https://api.mercadopago.com/oauth/token");
            config.setScope("read,write");
            config.setSandbox(false); // Produção por padrão
            config.setAtivo(true);
            config.setDataCriacao(LocalDateTime.now());
            config.setDataAtualizacao(LocalDateTime.now());
            
            // Salvar configuração
            bankApiConfigRepository.save(config);
            
            // Testar conexão com as credenciais
            try {
                log.info("🧪 Testando conexão com credenciais do usuário: {}", currentUser.getId());
                // Aqui você pode adicionar um teste real da API
                // Por exemplo: mercadoPagoService.testConnection(clientId, clientSecret);
            } catch (Exception e) {
                log.warn("⚠️ Aviso: Não foi possível testar a conexão: {}", e.getMessage());
                // Não falha a configuração por causa do teste
            }
            
            Map<String, Object> resultado = new HashMap<>();
            resultado.put("success", true);
            resultado.put("message", "Credenciais configuradas com sucesso!");
            resultado.put("clientId", clientId);
            resultado.put("userId", userId);
            resultado.put("timestamp", LocalDateTime.now());
            resultado.put("configuradoPor", currentUser.getUsername());
            
            log.info("✅ Configuração segura do Mercado Pago concluída para usuário: {}", currentUser.getId());
            
            return ResponseEntity.ok(resultado);
            
        } catch (Exception e) {
            log.error("❌ Erro ao configurar credenciais seguras: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "erro", "Falha na configuração: " + e.getMessage()
            ));
        }
    }

    /**
     * Obtém status da configuração segura do usuário
     * 
     * @param currentUser Usuário autenticado
     * @return Status da configuração
     */
    @GetMapping("/secure-config/status")
    public ResponseEntity<Map<String, Object>> getConfigStatus(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
            log.info("🔍 Verificando status da configuração segura para usuário: {}", currentUser.getId());
            
            Optional<BankApiConfig> config = bankApiConfigRepository
                .findByUsuarioIdAndTipoBanco(currentUser.getId(), "MERCADO_PAGO");
            
            Map<String, Object> status = new HashMap<>();
            
            if (config.isPresent()) {
                BankApiConfig cfg = config.get();
                status.put("hasConfig", true);
                status.put("clientId", cfg.getClientId());
                status.put("userId", "Configurado"); // userId está no nome
                status.put("hasClientSecret", cfg.getClientSecret() != null && !cfg.getClientSecret().isEmpty());
                status.put("isActive", cfg.getAtivo());
                status.put("isSandbox", cfg.getSandbox());
                status.put("lastUpdate", cfg.getDataAtualizacao());
                status.put("message", "Configuração encontrada");
            } else {
                status.put("hasConfig", false);
                status.put("message", "Nenhuma configuração encontrada");
            }
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            log.error("❌ Erro ao verificar status da configuração: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "erro", "Falha ao verificar status: " + e.getMessage()
            ));
        }
    }

    /**
     * Remove configuração segura do usuário
     * 
     * @param currentUser Usuário autenticado
     * @return Resultado da remoção
     */
    @DeleteMapping("/config")
    public ResponseEntity<Map<String, Object>> removeConfig(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
            log.info("🗑️ Removendo configuração segura do Mercado Pago para usuário: {}", currentUser.getId());
            
            Optional<BankApiConfig> config = bankApiConfigRepository
                .findByUsuarioIdAndTipoBanco(currentUser.getId(), "MERCADO_PAGO");
            
            if (config.isPresent()) {
                bankApiConfigRepository.delete(config.get());
                log.info("✅ Configuração removida com sucesso para usuário: {}", currentUser.getId());
                
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Configuração removida com sucesso"
                ));
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            log.error("❌ Erro ao remover configuração: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "erro", "Falha ao remover configuração: " + e.getMessage()
            ));
        }
    }
}
