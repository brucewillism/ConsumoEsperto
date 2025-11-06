package com.consumoesperto.controller;

import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Categoria;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.CategoriaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/verificar")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:4200", "https://0d723f1e294f.ngrok-free.app"})
public class VerificarTransacoesController {

    private final TransacaoRepository transacaoRepository;
    private final CategoriaRepository categoriaRepository;

    @GetMapping("/transacoes")
    public ResponseEntity<Map<String, Object>> verificarTransacoes() {
        try {
            List<Transacao> transacoes = transacaoRepository.findAll();
            
            log.info("📊 Total de transações no banco: {}", transacoes.size());
            
            for (Transacao transacao : transacoes) {
                log.info("   - {} | R$ {} | {}", 
                    transacao.getDescricao(), 
                    transacao.getValor(), 
                    transacao.getDataTransacao());
            }
            
            return ResponseEntity.ok(Map.of(
                "total_transacoes", transacoes.size(),
                "transacoes", transacoes,
                "status", "success"
            ));
            
        } catch (Exception e) {
            log.error("❌ Erro ao verificar transações: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }

}
