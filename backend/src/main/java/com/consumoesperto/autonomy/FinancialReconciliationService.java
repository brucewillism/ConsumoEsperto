package com.consumoesperto.autonomy;

import com.consumoesperto.config.FinancialAutonomyProperties;
import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import com.consumoesperto.model.OrigemTransacao;
import com.consumoesperto.service.importacao.FinancialImportDeduplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FinancialReconciliationService {

    private final FinancialImportDeduplicationService deduplicationService;
    private final TransactionEvidenceService evidenceService;
    private final FinancialAutonomyProperties properties;

    public Optional<FinancialImportDeduplicationService.Match> findExisting(
        Long usuarioId,
        ImportacaoFaturaItemDTO item,
        Long contaId,
        Long cartaoId
    ) {
        if (!properties.isReconciliation()) {
            return Optional.empty();
        }
        return deduplicationService.findExisting(usuarioId, item, contaId, cartaoId);
    }

    @Transactional
    public Long attachOrCreateEvidence(
        Long usuarioId,
        Long transacaoId,
        OrigemTransacao source,
        String externalId,
        String sourceEventId,
        BigDecimal confidence
    ) {
        String src = source != null ? source.name() : "UNKNOWN";
        evidenceService.record(usuarioId, transacaoId, src, externalId, sourceEventId, confidence, null);
        return transacaoId;
    }

    public List<String> sourcesOf(Long transacaoId) {
        return evidenceService.sources(transacaoId);
    }
}
