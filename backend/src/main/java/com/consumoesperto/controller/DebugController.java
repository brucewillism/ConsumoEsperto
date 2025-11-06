package com.consumoesperto.controller;

import com.consumoesperto.repository.AutorizacaoBancariaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Debug", description = "Endpoints de debug e limpeza")
@CrossOrigin(origins = {"http://localhost:4200", "https://0d723f1e294f.ngrok-free.app", "https://*.ngrok-free.app"})
public class DebugController {

    private final AutorizacaoBancariaRepository autorizacaoRepository;

    /**
     * Endpoint público para verificar status do token do Mercado Pago
     */
    @GetMapping("/public/verificar-token-mercadopago")
    @Operation(summary = "Verificar status do token", description = "Verifica o status atual do token do Mercado Pago")
    public ResponseEntity<Map<String, Object>> verificarTokenMercadoPago() {
        log.info("🔍 Verificando status do token do Mercado Pago...");
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Buscar autorização do Mercado Pago para usuário 1
            var auth = autorizacaoRepository.findByUsuarioIdAndTipoBanco(1L, "MERCADO_PAGO");
            
            if (auth.isPresent()) {
                var autorizacao = auth.get();
                response.put("status", "success");
                response.put("token_encontrado", true);
                response.put("access_token", autorizacao.getAccessToken() != null ? "***" + autorizacao.getAccessToken().substring(Math.max(0, autorizacao.getAccessToken().length() - 4)) : null);
                response.put("data_expiracao", autorizacao.getDataExpiracao());
                response.put("token_valido", autorizacao.getDataExpiracao() != null && autorizacao.getDataExpiracao().isAfter(java.time.LocalDateTime.now()));
                response.put("tipo_token", autorizacao.getAccessToken() != null && autorizacao.getAccessToken().contains("TEMPORARY_AUTH_") ? "TEMPORARIO" : "REAL");
                
                if (autorizacao.getDataExpiracao() != null) {
                    response.put("dias_para_expiracao", java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDateTime.now(), autorizacao.getDataExpiracao()));
                }
            } else {
                response.put("status", "success");
                response.put("token_encontrado", false);
                response.put("message", "Nenhum token encontrado para o Mercado Pago");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Erro ao verificar token: {}", e.getMessage(), e);
            response.put("status", "error");
            response.put("message", "Erro ao verificar token: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Endpoint público temporário para limpar token expirado do Mercado Pago
     */
    @GetMapping("/public/limpar-token-expirado")
    @Operation(summary = "Limpar token expirado", description = "Remove tokens temporários e expirados do Mercado Pago")
    public ResponseEntity<Map<String, Object>> limparTokenExpirado() {
        log.info("🧹 Iniciando limpeza de token expirado...");
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Contar tokens antes da limpeza
            long tokensAntes = autorizacaoRepository.count();
            log.info("📊 Tokens antes da limpeza: {}", tokensAntes);
            
            // Executar limpeza
            int tokensRemovidos = autorizacaoRepository.deleteByTipoBancoAndUsuarioIdAndTokenExpirado(
                "MERCADO_PAGO", 
                1L
            );
            
            // Contar tokens após a limpeza
            long tokensDepois = autorizacaoRepository.count();
            log.info("📊 Tokens após limpeza: {}", tokensDepois);
            
            response.put("status", "success");
            response.put("message", "Token expirado removido com sucesso!");
            response.put("tokens_removidos", tokensRemovidos);
            response.put("tokens_antes", tokensAntes);
            response.put("tokens_depois", tokensDepois);
            response.put("oauth2_url", "http://localhost:8080/api/oauth2/mercadopago/init");
            
            log.info("✅ Limpeza concluída! {} tokens removidos", tokensRemovidos);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Erro ao limpar token expirado: {}", e.getMessage(), e);
            response.put("status", "error");
            response.put("message", "Erro ao limpar token: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
