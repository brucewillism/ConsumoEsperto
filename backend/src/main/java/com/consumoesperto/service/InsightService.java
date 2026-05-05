package com.consumoesperto.service;

import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Análise de padrões de consumo (recorrência) por usuário — só usado quando o utilizador pede (ex.: GET_INSIGHTS).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InsightService {

    private static final int DIAS = 90;
    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    private final TransacaoRepository transacaoRepository;

    /**
     * Mensagem Markdown para WhatsApp com até 3 recorrências prováveis (últimos 90 dias).
     */
    public String montarRecorrenciasWhatsapp(Long userId) {
        log.info("[INSIGHT-LOG] Início análise 90d userId={}", userId);
        LocalDateTime inicio = LocalDate.now().minusDays(DIAS).atStartOfDay();
        List<Transacao> linhas = transacaoRepository.findDespesasConfirmadasDesde(userId, inicio);
        if (linhas.isEmpty()) {
            log.info("[INSIGHT-LOG] Sem despesas confirmadas no período userId={}", userId);
            return "Não há despesas *confirmadas* nos últimos 90 dias para analisar recorrência.";
        }

        Map<String, List<Transacao>> porChave = new HashMap<>();
        for (Transacao t : linhas) {
            String k = chaveAgrupamento(t.getDescricao());
            porChave.computeIfAbsent(k, x -> new ArrayList<>()).add(t);
        }

        List<InsightLinha> candidatos = new ArrayList<>();
        for (Map.Entry<String, List<Transacao>> e : porChave.entrySet()) {
            List<Transacao> txs = e.getValue();
            if (txs.size() < 3) {
                continue;
            }
            Set<YearMonth> meses = txs.stream()
                .map(t -> YearMonth.from(t.getDataTransacao().toLocalDate()))
                .collect(Collectors.toSet());
            if (meses.size() < 2) {
                continue;
            }
            List<BigDecimal> valores = txs.stream().map(Transacao::getValor).filter(Objects::nonNull).toList();
            if (valores.isEmpty()) {
                continue;
            }
            BigDecimal soma = valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal media = soma.divide(BigDecimal.valueOf(valores.size()), 2, RoundingMode.HALF_UP);
            BigDecimal min = valores.stream().min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
            BigDecimal max = valores.stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
            if (media.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal ratioMin = min.divide(media, 4, RoundingMode.HALF_UP);
            BigDecimal ratioMax = max.divide(media, 4, RoundingMode.HALF_UP);
            if (ratioMin.compareTo(new BigDecimal("0.78")) < 0 || ratioMax.compareTo(new BigDecimal("1.22")) > 0) {
                continue;
            }
            String rotulo = rotuloAmigavel(txs.get(0).getDescricao());
            candidatos.add(new InsightLinha(rotulo, media, meses.size(), txs.size()));
        }

        candidatos.sort(Comparator
            .comparingInt(InsightLinha::mesesDistintos).reversed()
            .thenComparing(InsightLinha::media));

        if (candidatos.isEmpty()) {
            log.info("[INSIGHT-LOG] Nenhum padrão forte userId={} (amostras={})", userId, linhas.size());
            return "Não identifiquei padrões claros de *recorrência mensal* nos últimos 90 dias. "
                + "Quando tiver mais lançamentos repetidos (ex.: streaming, academia), volte a perguntar.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📊 *Recorrência (últimos 90 dias)*\n\n");
        int lim = Math.min(3, candidatos.size());
        for (int i = 0; i < lim; i++) {
            InsightLinha x = candidatos.get(i);
            sb.append("• Notei que você gasta em média *")
                .append(BRL.format(x.media))
                .append("* com *")
                .append(x.nomeExibicao)
                .append("* (")
                .append(x.mesesDistintos)
                .append(" meses com valores parecidos). Quer que eu transforme isso em uma *Despesa fixa*? ")
                .append("Responda no app ou diga o que deseja alterar.\n");
        }
        sb.append("\n_Dica: insights só aparecem quando você pergunta — não envio alertas automáticos._");
        log.info("[INSIGHT-LOG] Retornando {} sugestões userId={}", lim, userId);
        return sb.toString().trim();
    }

    private static String chaveAgrupamento(String descricao) {
        if (descricao == null || descricao.isBlank()) {
            return "_vazio_";
        }
        String n = Normalizer.normalize(descricao, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        n = n.replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
        if (n.length() > 48) {
            n = n.substring(0, 48).trim();
        }
        return n.isBlank() ? "_vazio_" : n;
    }

    private static String rotuloAmigavel(String descricao) {
        if (descricao == null || descricao.isBlank()) {
            return "este estabelecimento";
        }
        String t = descricao.trim();
        if (t.length() > 42) {
            return t.substring(0, 39) + "...";
        }
        return t;
    }

    private record InsightLinha(String nomeExibicao, BigDecimal media, int mesesDistintos, int ocorrencias) {
    }
}
