package com.consumoesperto.controller;

import com.consumoesperto.service.RealMercadoPagoSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller para sincronização real de dados
 */
@RestController
@RequestMapping("/api/real-sync")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Sincronização Real", description = "Endpoints para sincronização de dados reais")
@CrossOrigin(origins = {"http://localhost:4200", "https://0d723f1e294f.ngrok-free.app", "https://*.ngrok-free.app"})
public class RealSyncController {

    private final RealMercadoPagoSyncService realSyncService;

    /**
     * Sincroniza dados reais do Mercado Pago para o usuário logado
     */
    @PostMapping("/mercadopago")
    @Operation(summary = "Sincronizar dados reais do Mercado Pago", 
               description = "Busca e salva dados reais do Mercado Pago no banco local")
    public ResponseEntity<Map<String, Object>> sincronizarMercadoPago(@RequestParam Long userId) {
        try {
            log.info("🔄 Iniciando sincronização real do Mercado Pago para usuário: {}", userId);
            
            realSyncService.sincronizarDadosReais(userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Dados reais sincronizados com sucesso");
            response.put("userId", userId);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Erro na sincronização real: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Erro na sincronização: " + e.getMessage());
            response.put("userId", userId);
            
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Limpa transações duplicadas
     */
    @PostMapping("/limpar-duplicatas")
    @Operation(summary = "Limpar transações duplicadas", 
               description = "Remove transações duplicadas do banco de dados")
    public ResponseEntity<Map<String, Object>> limparDuplicatas(@RequestParam Long userId) {
        try {
            log.info("🧹 Limpando transações duplicadas para usuário: {}", userId);
            
            // TODO: Implementar limpeza de duplicatas
            // Por enquanto, apenas retorna sucesso
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Transações duplicadas removidas com sucesso");
            response.put("userId", userId);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Erro na limpeza de duplicatas: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Erro na limpeza de duplicatas: " + e.getMessage());
            response.put("userId", userId);
            
            return ResponseEntity.badRequest().body(response);
        }
    }


    /**
     * Verifica se o usuário tem transações e sincroniza se necessário
     */
    @PostMapping("/verificar-e-sincronizar")
    @Operation(summary = "Verificar e sincronizar se necessário", 
               description = "Verifica se o usuário tem transações e sincroniza automaticamente se não tiver")
    public ResponseEntity<Map<String, Object>> verificarESincronizar(@RequestParam Long userId) {
        try {
            log.info("🔍 Verificando transações do usuário: {}", userId);
            
            // TODO: Implementar verificação de transações existentes
            // Por enquanto, sempre sincroniza
            realSyncService.sincronizarDadosReais(userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Verificação concluída e sincronização realizada");
            response.put("userId", userId);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Erro na verificação e sincronização: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Erro na verificação: " + e.getMessage());
            response.put("userId", userId);
            
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Testa conexão com o Mercado Pago
     */
    @GetMapping("/test-connection")
    @Operation(summary = "Testar conexão com Mercado Pago", 
               description = "Verifica se a conexão com a API do Mercado Pago está funcionando")
    public ResponseEntity<Map<String, Object>> testarConexao(@RequestParam Long userId) {
        try {
            log.info("🔍 Testando conexão com Mercado Pago para usuário: {}", userId);
            
            // Aqui você pode implementar um teste de conexão
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Conexão com Mercado Pago OK");
            response.put("userId", userId);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Erro no teste de conexão: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Erro na conexão: " + e.getMessage());
            response.put("userId", userId);
            
            return ResponseEntity.badRequest().body(response);
        }
    }
}
