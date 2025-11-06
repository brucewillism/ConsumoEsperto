package com.consumoesperto.controller;

import com.consumoesperto.service.AutoSyncService;
import com.consumoesperto.service.MercadoPagoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller para operações de sincronização automática
 */
@RestController
@RequestMapping("/api/auto-sync")
<<<<<<< HEAD
@CrossOrigin(origins = {"http://localhost:4200", "https://0d723f1e294f.ngrok-free.app"})
=======
@CrossOrigin(origins = {"http://localhost:4200", "https://22e294954ab2.ngrok-free.app"})
>>>>>>> origin/main
public class AutoSyncController {

    private static final Logger logger = LoggerFactory.getLogger(AutoSyncController.class);

    @Autowired
    private AutoSyncService autoSyncService;

    @Autowired
    private MercadoPagoService mercadoPagoService;

    /**
     * Força a sincronização de dados para um usuário específico
     */
    @PostMapping("/sincronizar/{userId}")
    public ResponseEntity<Map<String, Object>> sincronizarUsuario(@PathVariable Long userId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("🔄 Iniciando sincronização manual para usuário: {}", userId);
            
            // Verificar se o usuário tem configuração do Mercado Pago
            if (mercadoPagoService.possuiConfiguracaoAtiva(userId)) {
                // Sincronizar dados
                mercadoPagoService.sincronizarDadosAutomaticamente(userId);
                
                response.put("success", true);
                response.put("message", "Dados sincronizados com sucesso para o usuário " + userId);
                response.put("userId", userId);
                
                logger.info("✅ Sincronização manual concluída para usuário: {}", userId);
                
            } else {
                response.put("success", false);
                response.put("message", "Usuário não possui configuração ativa do Mercado Pago");
                response.put("userId", userId);
                
                logger.warn("⚠️ Usuário {} não possui configuração do Mercado Pago", userId);
            }
            
        } catch (Exception e) {
            logger.error("❌ Erro na sincronização manual para usuário {}: {}", userId, e.getMessage(), e);
            
            response.put("success", false);
            response.put("message", "Erro na sincronização: " + e.getMessage());
            response.put("userId", userId);
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * Força a sincronização de dados para todos os usuários
     */
    @PostMapping("/sincronizar-todos")
    public ResponseEntity<Map<String, Object>> sincronizarTodos() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("🔄 Iniciando sincronização manual para todos os usuários");
            
            // Executar sincronização automática
            autoSyncService.onApplicationReady();
            
            response.put("success", true);
            response.put("message", "Dados sincronizados com sucesso para todos os usuários");
            
            logger.info("✅ Sincronização manual concluída para todos os usuários");
            
        } catch (Exception e) {
            logger.error("❌ Erro na sincronização manual: {}", e.getMessage(), e);
            
            response.put("success", false);
            response.put("message", "Erro na sincronização: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * Força sincronização completa (preservando dados históricos)
     */
    @PostMapping("/sincronizar-completo")
    public ResponseEntity<Map<String, Object>> sincronizarCompleto() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("🔄 Iniciando sincronização completa (preservando dados históricos)");
            
            // Executar sincronização sem limpar dados antigos
            autoSyncService.onApplicationReady();
            
            response.put("success", true);
            response.put("message", "Dados sincronizados com sucesso (dados históricos preservados)");
            
            logger.info("✅ Sincronização completa concluída");
            
        } catch (Exception e) {
            logger.error("❌ Erro na sincronização completa: {}", e.getMessage(), e);
            
            response.put("success", false);
            response.put("message", "Erro na sincronização: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * Verifica o status da sincronização
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            response.put("success", true);
            response.put("message", "Sistema de sincronização automática ativo");
            response.put("timestamp", System.currentTimeMillis());
            
        } catch (Exception e) {
            logger.error("❌ Erro ao verificar status: {}", e.getMessage(), e);
            
            response.put("success", false);
            response.put("message", "Erro ao verificar status: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
}
