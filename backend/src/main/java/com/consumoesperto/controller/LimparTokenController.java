package com.consumoesperto.controller;

import com.consumoesperto.repository.AutorizacaoBancariaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/limpar")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:4200", "https://0d723f1e294f.ngrok-free.app", "*"})
public class LimparTokenController {

    private final AutorizacaoBancariaRepository autorizacaoRepository;

    @GetMapping("/token")
    public Map<String, Object> limparToken() {
        log.info("🧹 Iniciando limpeza de token expirado...");
        
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
            
            log.info("✅ Limpeza concluída! {} tokens removidos", tokensRemovidos);
            
            return Map.of(
                "status", "success",
                "message", "Token expirado removido com sucesso!",
                "tokens_removidos", tokensRemovidos,
                "tokens_antes", tokensAntes,
                "tokens_depois", tokensDepois,
                "oauth2_url", "http://localhost:8080/api/oauth2/mercadopago/init"
            );
            
        } catch (Exception e) {
            log.error("❌ Erro ao limpar token expirado: {}", e.getMessage(), e);
            return Map.of(
                "status", "error",
                "message", "Erro ao limpar token: " + e.getMessage()
            );
        }
    }
}
