package com.consumoesperto.autonomy;

import com.consumoesperto.config.FinancialAutonomyProperties;
import com.consumoesperto.dto.TransacaoIngestSnapshot;
import com.consumoesperto.mobilecapture.service.MerchantNormalizationService;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.service.importacao.FinancialImportDeduplicationService;
import com.consumoesperto.util.MoedaUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Cobrança duplicada: alerta, nunca exclusão automática.
 */
@Service
@RequiredArgsConstructor
public class DuplicateChargeDetectionService {

    private final TransacaoRepository transacaoRepository;
    private final MerchantNormalizationService merchantNormalizationService;
    private final FinancialAutonomyProperties properties;

    @Transactional(readOnly = true)
    public List<Transacao> findPossibleDuplicates(Long usuarioId, TransacaoIngestSnapshot snap) {
        if (snap == null || snap.valor() == null || snap.dataTransacao() == null) {
            return List.of();
        }
        int window = Math.max(1, properties.getDuplicateWindowMinutes());
        LocalDateTime inicio = snap.dataTransacao().minusMinutes(window);
        LocalDateTime fim = snap.dataTransacao().plusMinutes(window);
        String merchant = merchantNormalizationService.normalize(
            first(snap.merchantNormalized(), snap.merchantRaw(), snap.descricao()));
        List<Transacao> out = new ArrayList<>();
        for (Transacao t : transacaoRepository.findByUsuarioIdAndPeriodoEfetivoOrderByDataDesc(usuarioId, inicio, fim)) {
            if (t.isExcluido() || Objects.equals(t.getId(), snap.id())) {
                continue;
            }
            if (!FinancialImportDeduplicationService.valoresCompativeis(t.getValor(), snap.valor())) {
                continue;
            }
            if (snap.cartaoId() != null && t.getFatura() != null && t.getFatura().getCartaoCredito() != null
                && !snap.cartaoId().equals(t.getFatura().getCartaoCredito().getId())) {
                continue;
            }
            String other = merchantNormalizationService.normalize(
                first(t.getMerchantNormalized(), t.getDescricao()));
            if (merchant == null || other == null || merchant.isBlank() || other.isBlank()) {
                continue;
            }
            if (!FinancialImportDeduplicationService.descricaoCompativel(merchant, other)) {
                continue;
            }
            if (t.getDataTransacao() != null
                && Math.abs(ChronoUnit.MINUTES.between(t.getDataTransacao(), snap.dataTransacao())) > window) {
                continue;
            }
            out.add(t);
        }
        return out;
    }

    public BigDecimal amountOf(TransacaoIngestSnapshot snap) {
        return MoedaUtil.nz(snap == null ? null : snap.valor());
    }

    private static String first(String... vals) {
        if (vals == null) {
            return null;
        }
        for (String v : vals) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
