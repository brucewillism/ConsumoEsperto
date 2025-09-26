package com.consumoesperto.controller;

import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.MercadoPagoAutoConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller para configuração automática do Mercado Pago
 * 
 * Este controller permite configurar automaticamente as credenciais
 * do Mercado Pago usando as credenciais padrão do banco de dados.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@RestController
@RequestMapping("/api/mercadopago/auto-config")
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoAutoConfigController {

    private final MercadoPagoAutoConfigService autoConfigService;

    /**
     * Configura automaticamente as credenciais do Mercado Pago para o usuário atual
     * 
     * @param currentUser Usuário autenticado
     * @return Resultado da configuração
     */
    @PostMapping("/configure")
    public ResponseEntity<Map<String, Object>> configurarCredenciais(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
            log.info("🔧 Configurando credenciais automáticas para usuário: {}", currentUser.getId());
            
            boolean sucesso = autoConfigService.configurarCredenciaisAutomaticas(currentUser.getId());
            
            Map<String, Object> resultado = new HashMap<>();
            resultado.put("success", sucesso);
            
            if (sucesso) {
                resultado.put("message", "Credenciais configuradas automaticamente com sucesso!");
                resultado.put("clientId", "4223603750190943");
                resultado.put("userId", "209112973");
                resultado.put("timestamp", java.time.LocalDateTime.now());
                
                log.info("✅ Configuração automática concluída para usuário: {}", currentUser.getId());
            } else {
                resultado.put("message", "Erro ao configurar credenciais automaticamente");
                log.warn("❌ Falha na configuração automática para usuário: {}", currentUser.getId());
            }
            
            return ResponseEntity.ok(resultado);
            
        } catch (Exception e) {
            log.error("❌ Erro ao configurar credenciais automáticas: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "erro", "Falha na configuração: " + e.getMessage()
            ));
        }
    }

    /**
     * Configura credenciais para todos os usuários
     * 
     * @return Resultado da configuração
     */
    @PostMapping("/configure-all")
    public ResponseEntity<Map<String, Object>> configurarTodosUsuarios() {
        try {
            log.info("🔧 Configurando credenciais automáticas para todos os usuários...");
            
            int configurados = autoConfigService.configurarTodosUsuarios();
            
            Map<String, Object> resultado = new HashMap<>();
            resultado.put("success", true);
            resultado.put("message", "Configuração automática concluída");
            resultado.put("usuariosConfigurados", configurados);
            resultado.put("timestamp", java.time.LocalDateTime.now());
            
            log.info("✅ Configuração automática concluída: {} usuários", configurados);
            
            return ResponseEntity.ok(resultado);
            
        } catch (Exception e) {
            log.error("❌ Erro ao configurar todos os usuários: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "erro", "Falha na configuração: " + e.getMessage()
            ));
        }
    }

    /**
     * Verifica se o usuário tem configuração ativa
     * 
     * @param currentUser Usuário autenticado
     * @return Status da configuração
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> verificarStatus(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
            boolean temConfiguracao = autoConfigService.temConfiguracaoAtiva(currentUser.getId());
            
            Map<String, Object> status = new HashMap<>();
            status.put("temConfiguracao", temConfiguracao);
            status.put("usuarioId", currentUser.getId());
            status.put("timestamp", java.time.LocalDateTime.now());
            
            if (temConfiguracao) {
                status.put("message", "Configuração ativa encontrada");
                status.put("clientId", "4223603750190943");
                status.put("userId", "209112973");
            } else {
                status.put("message", "Nenhuma configuração encontrada");
            }
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            log.error("❌ Erro ao verificar status: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "erro", "Falha ao verificar status: " + e.getMessage()
            ));
        }
    }
}
