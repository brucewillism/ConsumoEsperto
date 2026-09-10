package com.consumoesperto.service.importacao;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import com.consumoesperto.mobilecapture.service.MerchantNormalizationService;
import com.consumoesperto.model.OrigemTransacao;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.util.MoedaUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Deduplicação canônica (sem origem) para CSV e captura móvel convergirem na mesma transação.
 */
@Service
@RequiredArgsConstructor
public class FinancialImportDeduplicationService {

    private final TransacaoRepository transacaoRepository;
    private final MerchantNormalizationService merchantNormalizationService;

    public String buildCanonicalFingerprint(
        Long usuarioId,
        Long contaId,
        Long cartaoId,
        LocalDate date,
        BigDecimal amount,
        String merchantOrDescription,
        Integer parcelaAtual,
        Integer totalParcelas,
        String tipoLinha
    ) {
        String merchant = merchantNormalizationService.normalize(
            merchantOrDescription != null ? merchantOrDescription : "");
        if (merchant == null) {
            merchant = "";
        }
        String amountPlain = amount == null
            ? "0"
            : amount.abs().stripTrailingZeros().toPlainString();
        String payload = usuarioId + "|"
            + n(contaId) + "|"
            + n(cartaoId) + "|"
            + (date == null ? "na" : date.toString()) + "|"
            + amountPlain + "|"
            + merchant + "|"
            + n(parcelaAtual) + "|"
            + n(totalParcelas) + "|"
            + (tipoLinha == null ? "DESPESA" : tipoLinha);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    public Optional<Match> findExisting(Long usuarioId, ImportacaoFaturaItemDTO item, Long contaId, Long cartaoId) {
        if (item.getExternalId() != null && !item.getExternalId().isBlank()) {
            Optional<Transacao> byExt = transacaoRepository
                .findFirstByUsuarioIdAndExternalEventId(usuarioId, item.getExternalId().trim());
            if (byExt.isPresent()) {
                return Optional.of(toMatch(byExt.get()));
            }
        }
        if (item.getFingerprint() != null && !item.getFingerprint().isBlank()) {
            Optional<Transacao> byFp = transacaoRepository
                .findFirstByUsuarioIdAndIngestionFingerprint(usuarioId, item.getFingerprint());
            if (byFp.isPresent()) {
                return Optional.of(toMatch(byFp.get()));
            }
        }
        if (item.getData() == null || item.getValor() == null || item.getDescricao() == null) {
            return Optional.empty();
        }
        if (item.getTipoLinha() != null && !"DESPESA".equals(item.getTipoLinha())
            && !"TRANSFERENCIA".equals(item.getTipoLinha())) {
            return Optional.empty();
        }
        LocalDateTime dt = item.getData().atStartOfDay();
        List<Transacao> window = transacaoRepository.findByUsuarioIdAndPeriodoEfetivoOrderByDataDesc(
            usuarioId, dt.minusDays(1), dt.plusDays(2));
        String descNorm = normalizeDesc(item.getMerchant() != null ? item.getMerchant() : item.getDescricao());
        for (Transacao t : window) {
            if (t.isExcluido()) {
                continue;
            }
            if (!valoresCompativeis(t.getValor(), item.getValor())) {
                continue;
            }
            if (item.getParcelaAtual() != null && t.getParcelaAtual() != null
                && !item.getParcelaAtual().equals(t.getParcelaAtual())) {
                continue;
            }
            if (cartaoId != null && t.getFatura() != null && t.getFatura().getCartaoCredito() != null
                && !cartaoId.equals(t.getFatura().getCartaoCredito().getId())) {
                continue;
            }
            if (contaId != null && t.getContaBancaria() != null
                && !contaId.equals(t.getContaBancaria().getId())
                && t.getFatura() == null) {
                continue;
            }
            String existing = firstNonBlank(t.getMerchantNormalized(), t.getDescricao());
            if (descricaoCompativel(descNorm, normalizeDesc(existing))) {
                return Optional.of(toMatch(t));
            }
        }
        return Optional.empty();
    }

    public Match toMatch(Transacao t) {
        OrigemTransacao origem = t.getOrigemTransacao();
        boolean mobile = origem == OrigemTransacao.IOS_WALLET
            || origem == OrigemTransacao.ANDROID_NOTIFICATION
            || origem == OrigemTransacao.NOTIFICACAO_BANCARIA;
        boolean alreadyImported = origem == OrigemTransacao.CSV_BANK_STATEMENT
            || origem == OrigemTransacao.CSV_CARD_STATEMENT
            || origem == OrigemTransacao.FATURA_PDF;
        Kind kind = mobile ? Kind.MATCHED_EXISTING : (alreadyImported ? Kind.DUPLICATE : Kind.MATCHED_EXISTING);
        return new Match(t, kind);
    }

    public enum Kind { DUPLICATE, MATCHED_EXISTING }

    public record Match(Transacao transacao, Kind kind) {}

    public static boolean valoresCompativeis(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return false;
        }
        return MoedaUtil.nz(a).abs().compareTo(MoedaUtil.nz(b).abs()) == 0;
    }

    public static boolean descricaoCompativel(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return false;
        }
        return a.equals(b) || a.contains(b) || b.contains(a);
    }

    static String normalizeDesc(String raw) {
        if (raw == null) {
            return "";
        }
        String n = Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toUpperCase(Locale.ROOT)
            .replaceAll("\\(\\d+/\\d+\\)", "")
            .replaceAll("[^A-Z0-9\\s]", " ")
            .replaceAll("\\s+", " ")
            .trim();
        return n;
    }

    private static String n(Object v) {
        return v == null ? "na" : String.valueOf(v);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }
}
