package com.consumoesperto.service;

import com.consumoesperto.model.MemoriaCategoriaOrigem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Gravações de memória fora do thread crítico (insights, digest).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CerebroSemanticoAsync {

    private final CerebroSemanticoService cerebroSemanticoService;

    @Async("cerebroExecutor")
    public void registrarAposInsightsFinanceiros(Long usuarioId, String resumoMarkdown) {
        if (usuarioId == null || resumoMarkdown == null || resumoMarkdown.isBlank()) {
            return;
        }
        String ctx = resumoMarkdown.replace('*', ' ').replace('_', ' ').replace('\n', ' ').trim().replaceAll("\\s+", " ");
        if (ctx.length() > 1600) {
            ctx = ctx.substring(0, 1600);
        }
        try {
            cerebroSemanticoService.gravarMemoria(
                usuarioId,
                "Resumo tático de insights: " + ctx,
                MemoriaCategoriaOrigem.FINANCAS);
        } catch (Exception e) {
            log.warn("Memória assíncrona (insights) não gravada: {}", e.getMessage());
        }
    }
}
