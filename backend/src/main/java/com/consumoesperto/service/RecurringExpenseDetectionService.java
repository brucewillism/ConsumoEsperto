package com.consumoesperto.service;

import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecurringExpenseDetectionService {

    private final TransacaoRepository transacaoRepository;

    @Transactional(readOnly = true)
    public List<RecurringExpense> detectar(Long usuarioId) {
        LocalDateTime inicio = YearMonth.now().minusMonths(5).atDay(1).atStartOfDay();
        LocalDateTime fim = LocalDateTime.now();
        List<Transacao> despesas = transacaoRepository.findByUsuarioIdAndTipoAndPeriodo(
            usuarioId, Transacao.TipoTransacao.DESPESA, inicio, fim);
        Map<String, List<Transacao>> grupos = despesas.stream()
            .filter(t -> t.getDescricao() != null && t.getValor() != null)
            .collect(Collectors.groupingBy(t -> chave(t), LinkedHashMap::new, Collectors.toList()));
        List<RecurringExpense> out = new ArrayList<>();
        for (List<Transacao> grupo : grupos.values()) {
            Map<YearMonth, List<Transacao>> porMes = grupo.stream()
                .filter(t -> t.getDataTransacao() != null)
                .collect(Collectors.groupingBy(t -> YearMonth.from(t.getDataTransacao())));
            if (porMes.size() < 2) {
                continue;
            }
            List<Transacao> ordenadas = grupo.stream()
                .sorted(Comparator.comparing(Transacao::getDataTransacao))
                .collect(Collectors.toList());
            BigDecimal media = grupo.stream()
                .map(Transacao::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(grupo.size()), 2, RoundingMode.HALF_UP);
            int diaMedio = Math.max(1, (int) Math.round(grupo.stream()
                .mapToInt(t -> t.getDataTransacao().getDayOfMonth())
                .average()
                .orElse(1)));
            Transacao base = ordenadas.get(ordenadas.size() - 1);
            out.add(new RecurringExpense(base.getDescricao(), media, diaMedio, proximaData(diaMedio), grupo.size()));
        }
        return out;
    }

    private static LocalDate proximaData(int dia) {
        YearMonth ym = YearMonth.now();
        LocalDate candidate = ym.atDay(Math.min(dia, ym.lengthOfMonth()));
        if (!candidate.isAfter(LocalDate.now())) {
            YearMonth next = ym.plusMonths(1);
            return next.atDay(Math.min(dia, next.lengthOfMonth()));
        }
        return candidate;
    }

    private static String chave(Transacao t) {
        String desc = normalizar(t.getDescricao())
            .replaceAll("\\b(parcela|pagamento|compra|debito|credito|cartao|pix)\\b", "")
            .replaceAll("\\s+", " ")
            .trim();
        if (desc.length() > 24) {
            desc = desc.substring(0, 24);
        }
        BigDecimal bucket = t.getValor().divide(BigDecimal.TEN, 0, RoundingMode.HALF_UP).multiply(BigDecimal.TEN);
        return desc + "|" + bucket;
    }

    private static String normalizar(String raw) {
        if (raw == null) {
            return "";
        }
        return Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9 ]", " ");
    }

    public record RecurringExpense(String nome, BigDecimal valorMedio, int diaMedio, LocalDate proximaData, int ocorrencias) {
    }
}
