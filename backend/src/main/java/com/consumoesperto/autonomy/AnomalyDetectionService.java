package com.consumoesperto.autonomy;

import com.consumoesperto.config.FinancialAutonomyProperties;
import com.consumoesperto.mobilecapture.service.MerchantNormalizationService;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.util.AppTimeZone;
import com.consumoesperto.util.MoedaUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Anomalias determinísticas — não é z-score.
 * Compara gasto 30d × média 30–90d e variação de recorrência/assinatura.
 * Sem modelo único: classificação PARCIAL.
 * E.D.I.T.H. só interpreta depois, se a policy pedir.
 */
@Service
@RequiredArgsConstructor
public class AnomalyDetectionService {

    private final TransacaoRepository transacaoRepository;
    private final MerchantNormalizationService merchantNormalizationService;
    private final FinancialAutonomyProperties properties;

    public record Anomaly(String code, String title, String detail, Long transacaoId, BigDecimal confidence) {}

    @Transactional(readOnly = true)
    public List<Anomaly> detectar(Long usuarioId, Long focusTransacaoId) {
        if (!properties.isAnomaly()) {
            return List.of();
        }
        LocalDateTime fim = AppTimeZone.agora();
        LocalDateTime inicio = fim.minusDays(90);
        List<Transacao> despesas = transacaoRepository.findByUsuarioIdAndTipoAndPeriodo(
            usuarioId, Transacao.TipoTransacao.DESPESA, inicio, fim).stream()
            .filter(t -> !t.isExcluido())
            .toList();
        List<Anomaly> out = new ArrayList<>();
        out.addAll(merchantNovoAlto(despesas, focusTransacaoId));
        out.addAll(aumentoCategoria(despesas));
        out.addAll(precoAssinatura(despesas));
        return out;
    }

    private List<Anomaly> merchantNovoAlto(List<Transacao> despesas, Long focusId) {
        Map<String, Long> counts = despesas.stream()
            .collect(Collectors.groupingBy(this::chaveMerchant, Collectors.counting()));
        List<Anomaly> out = new ArrayList<>();
        for (Transacao t : despesas) {
            if (focusId != null && !focusId.equals(t.getId())) {
                continue;
            }
            String key = chaveMerchant(t);
            if (key.isBlank() || counts.getOrDefault(key, 0L) > 1) {
                continue;
            }
            BigDecimal valor = MoedaUtil.nz(t.getValor()).abs();
            if (valor.compareTo(new BigDecimal("250")) < 0) {
                continue;
            }
            out.add(new Anomaly(
                "NEW_HIGH_MERCHANT",
                "Estabelecimento novo de valor alto",
                truncate(t.getDescricao()) + " · " + valor,
                t.getId(),
                new BigDecimal("0.82")
            ));
        }
        return out;
    }

    private List<Anomaly> aumentoCategoria(List<Transacao> despesas) {
        LocalDateTime corte = AppTimeZone.agora().minusDays(30);
        Map<Long, BigDecimal> recente = new LinkedHashMap<>();
        Map<Long, BigDecimal> anterior = new LinkedHashMap<>();
        Map<Long, String> nomes = new LinkedHashMap<>();
        for (Transacao t : despesas) {
            if (t.getCategoria() == null || t.getDataTransacao() == null) {
                continue;
            }
            Long cid = t.getCategoria().getId();
            nomes.put(cid, t.getCategoria().getNome());
            BigDecimal v = MoedaUtil.nz(t.getValor()).abs();
            if (!t.getDataTransacao().isBefore(corte)) {
                recente.merge(cid, v, BigDecimal::add);
            } else {
                anterior.merge(cid, v, BigDecimal::add);
            }
        }
        List<Anomaly> out = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> e : recente.entrySet()) {
            BigDecimal prev = anterior.getOrDefault(e.getKey(), BigDecimal.ZERO);
            if (prev.compareTo(new BigDecimal("50")) < 0) {
                continue;
            }
            BigDecimal ratio = e.getValue().divide(prev, 2, RoundingMode.HALF_UP);
            if (ratio.compareTo(new BigDecimal("1.40")) >= 0) {
                String nome = nomes.getOrDefault(e.getKey(), "categoria");
                out.add(new Anomaly(
                    "CATEGORY_SPIKE",
                    nome + " acima da média",
                    nome + " está " + ratio + "x o período anterior",
                    null,
                    new BigDecimal("0.84")
                ));
            }
        }
        return out;
    }

    private List<Anomaly> precoAssinatura(List<Transacao> despesas) {
        Map<String, List<Transacao>> grupos = despesas.stream()
            .filter(t -> t.getDataTransacao() != null)
            .collect(Collectors.groupingBy(this::chaveMerchant));
        List<Anomaly> out = new ArrayList<>();
        for (List<Transacao> grupo : grupos.values()) {
            if (grupo.size() < 2) {
                continue;
            }
            List<Transacao> ordenadas = grupo.stream()
                .sorted((a, b) -> a.getDataTransacao().compareTo(b.getDataTransacao()))
                .toList();
            Transacao last = ordenadas.get(ordenadas.size() - 1);
            Transacao prev = ordenadas.get(ordenadas.size() - 2);
            BigDecimal a = MoedaUtil.nz(last.getValor()).abs();
            BigDecimal b = MoedaUtil.nz(prev.getValor()).abs();
            if (b.compareTo(BigDecimal.ZERO) <= 0 || a.compareTo(b) == 0) {
                continue;
            }
            long days = java.time.temporal.ChronoUnit.DAYS.between(
                prev.getDataTransacao().toLocalDate(), last.getDataTransacao().toLocalDate());
            if (days < 20 || days > 40) {
                continue;
            }
            if (a.subtract(b).abs().compareTo(new BigDecimal("1")) < 0) {
                continue;
            }
            out.add(new Anomaly(
                "SUBSCRIPTION_PRICE_CHANGE",
                "Possível aumento de assinatura",
                truncate(last.getDescricao()) + " " + b + " → " + a,
                last.getId(),
                new BigDecimal("0.88")
            ));
        }
        return out;
    }

    private String chaveMerchant(Transacao t) {
        String n = merchantNormalizationService.normalize(
            t.getMerchantNormalized() != null ? t.getMerchantNormalized() : t.getDescricao());
        return n == null ? "" : n;
    }

    private static String truncate(String v) {
        if (v == null) {
            return "";
        }
        return v.length() <= 80 ? v : v.substring(0, 80);
    }
}
