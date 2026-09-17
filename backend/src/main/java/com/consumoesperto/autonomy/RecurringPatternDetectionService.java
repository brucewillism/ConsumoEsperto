package com.consumoesperto.autonomy;

import com.consumoesperto.model.AutonomyPreferencia;
import com.consumoesperto.service.RecurringExpenseDetectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Detecta padrões. Não cria assinatura permanente (REQUIRE_CONFIRMATION).
 */
@Service
@RequiredArgsConstructor
public class RecurringPatternDetectionService {

    private final RecurringExpenseDetectionService recurringExpenseDetectionService;
    private final AutonomyReviewService reviewService;
    private final JarvisProactiveOrchestrator jarvis;
    private final AutonomyPreferenciaService preferenciaService;

    public record Suggestion(String nome, BigDecimal valor, int dia, int ocorrencias) {}

    @Transactional
    public List<Suggestion> detectarESugerir(Long usuarioId) {
        AutonomyPreferencia pref = preferenciaService.obterOuCriar(usuarioId);
        if (!pref.isDetectarAssinaturas()) {
            return List.of();
        }
        List<Suggestion> out = new ArrayList<>();
        for (var rec : recurringExpenseDetectionService.detectar(usuarioId)) {
            if (rec.ocorrencias() < 2) {
                continue;
            }
            Suggestion s = new Suggestion(rec.nome(), rec.valorMedio(), rec.diaMedio(), rec.ocorrencias());
            out.add(s);
            reviewService.open(
                usuarioId,
                "RECURRENCE",
                null,
                "Possível recorrência: " + rec.nome(),
                "Valor médio " + rec.valorMedio() + " no dia " + rec.diaMedio()
                    + ". Confirme para tratar como assinatura.",
                new BigDecimal("0.75")
            );
            jarvis.notify(
                usuarioId,
                JarvisNotificationPriority.ACTION_REQUIRED,
                "Recorrência detectada",
                "Detectei uma cobrança periódica de " + rec.nome()
                    + " de R$ " + rec.valorMedio()
                    + ". Deseja tratá-la como assinatura?"
            );
        }
        return out;
    }
}
