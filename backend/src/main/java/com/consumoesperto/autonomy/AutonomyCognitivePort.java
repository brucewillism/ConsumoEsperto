package com.consumoesperto.autonomy;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Porta cognitiva — ConsumoEsperto não escolhe provider. E.D.I.T.H. (ou noop) decide.
 */
public interface AutonomyCognitivePort {

    Optional<ClassificationHint> classifyMerchant(Long usuarioId, String merchant, String descricao);

    record ClassificationHint(
        Long categoriaId,
        String categoriaNome,
        BigDecimal confidence,
        String edithTaskId,
        String conversationId,
        String requestId
    ) {
        public ClassificationHint(Long categoriaId, String categoriaNome, BigDecimal confidence, String edithTaskId) {
            this(categoriaId, categoriaNome, confidence, edithTaskId, null, null);
        }
    }
}
