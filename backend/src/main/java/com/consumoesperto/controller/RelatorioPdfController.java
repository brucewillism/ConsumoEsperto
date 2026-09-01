package com.consumoesperto.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoints legados de "PDF" descontinuados.
 *
 * Os antigos endpoints devolviam texto UTF-8 com {@code Content-Type: application/pdf}
 * — um arquivo inválido que enganava o consumidor. Foram descontinuados com
 * {@code 410 Gone} apontando para os relatórios PDF reais:
 *
 * <ul>
 *   <li>{@code GET /api/relatorios/mensal.pdf?ano=&mes=} — resumo mensal (PDF real)</li>
 *   <li>{@code GET /api/relatorios/exportar-ir.pdf?ano=} — exportação IR (PDF real)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/relatorios")
@Slf4j
@CrossOrigin(originPatterns = {"http://localhost:14200", "https://*.ngrok-free.app", "https://*.ngrok.io"})
public class RelatorioPdfController {

    private static final String SUBSTITUTO_MENSAL = "/api/relatorios/mensal.pdf?ano={ano}&mes={mes}";

    @GetMapping("/financeiro-completo")
    public ResponseEntity<Map<String, String>> financeiroCompletoDescontinuado() {
        return gone("financeiro-completo");
    }

    @GetMapping("/transacoes")
    public ResponseEntity<Map<String, String>> transacoesDescontinuado() {
        return gone("transacoes");
    }

    @GetMapping("/faturas")
    public ResponseEntity<Map<String, String>> faturasDescontinuado() {
        return gone("faturas");
    }

    @GetMapping("/disponiveis")
    public ResponseEntity<Map<String, Object>> disponiveis() {
        return ResponseEntity.ok(Map.of(
            "relatorios", java.util.List.of(
                Map.of(
                    "id", "mensal",
                    "nome", "Relatório mensal (PDF)",
                    "endpoint", "/api/relatorios/mensal.pdf",
                    "parametros", java.util.List.of("ano", "mes")
                ),
                Map.of(
                    "id", "exportar-ir",
                    "nome", "Exportação IR (PDF)",
                    "endpoint", "/api/relatorios/exportar-ir.pdf",
                    "parametros", java.util.List.of("ano")
                )
            )
        ));
    }

    private ResponseEntity<Map<String, String>> gone(String endpoint) {
        log.info("[RELATORIO-LEGADO] Endpoint descontinuado acessado: {}", endpoint);
        return ResponseEntity.status(HttpStatus.GONE)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of(
                "error", "gone",
                "message", "Este endpoint foi descontinuado: devolvia texto simples rotulado como PDF. "
                    + "Use o relatório PDF real.",
                "substituto", SUBSTITUTO_MENSAL
            ));
    }
}
