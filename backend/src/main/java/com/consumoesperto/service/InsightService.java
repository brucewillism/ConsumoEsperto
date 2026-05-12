package com.consumoesperto.service;

import com.consumoesperto.dto.GatilhoHabitoDeteccaoDTO;
import com.consumoesperto.dto.MemoriaSemanticaSimilaridadeDTO;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Análise de padrões de consumo (recorrência) por usuário — só usado quando o utilizador pede (ex.: GET_INSIGHTS).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class InsightService {

    private static final int DIAS = 90;
    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    private static final DateTimeFormatter MES_ANO_PT = DateTimeFormatter.ofPattern("MMMM/yyyy", new Locale("pt", "BR"));

    private final TransacaoRepository transacaoRepository;
    private final ScoreService scoreService;
    private final ContencaoJarvisService contencaoJarvisService;
    private final CerebroSemanticoService cerebroSemanticoService;
    private final CerebroSemanticoAsync cerebroSemanticoAsync;
    private final JarvisProtocolService jarvisProtocolService;
    private final UsuarioRepository usuarioRepository;
    private final JarvisFeedbackService jarvisFeedbackService;

    /**
     * Mensagem Markdown para WhatsApp com até 3 recorrências prováveis (últimos 90 dias).
     */
    public String montarRecorrenciasWhatsapp(Long userId) {
        log.info("[INSIGHT-LOG] Início análise 90d userId={}", userId);
        String alertaDomino = montarAlertaGatilhoHabito2h(userId);
        LocalDateTime inicio = LocalDate.now().minusDays(DIAS).atStartOfDay();
        List<Transacao> linhas = transacaoRepository.findDespesasConfirmadasDesde(userId, inicio);
        if (linhas.isEmpty()) {
            log.info("[INSIGHT-LOG] Sem despesas confirmadas no período userId={}", userId);
            String soProativa = contencaoJarvisService.blocoInsightsHabito3Meses(userId);
            String prefix = alertaDomino.isBlank() ? "" : alertaDomino + "\n\n";
            if (soProativa != null && !soProativa.isBlank()) {
                return prefix + "Não há despesas *confirmadas* nos últimos 90 dias para analisar recorrência." + soProativa;
            }
            return prefix + "Não há despesas *confirmadas* nos últimos 90 dias para analisar recorrência.";
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
            int pctAumentoVsMedia = 20;
            if (media.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal raw = max.subtract(media).divide(media, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
                pctAumentoVsMedia = raw.max(BigDecimal.ZERO)
                    .setScale(0, RoundingMode.HALF_UP)
                    .min(BigDecimal.valueOf(400))
                    .intValue();
            }
            if (pctAumentoVsMedia < 10) {
                pctAumentoVsMedia = 20;
            }
            candidatos.add(new InsightLinha(rotulo, media, meses.size(), txs.size(), pctAumentoVsMedia, e.getKey()));
        }

        candidatos.sort(Comparator
            .comparingInt(InsightLinha::mesesDistintos).reversed()
            .thenComparing(InsightLinha::media));

        if (candidatos.isEmpty()) {
            log.info("[INSIGHT-LOG] Nenhum padrão forte userId={} (amostras={})", userId, linhas.size());
            String base = "Não identifiquei padrões claros de *recorrência mensal* nos últimos 90 dias. "
                + "Quando tiver mais lançamentos repetidos (ex.: streaming, academia), volte a perguntar.";
            String prefix = alertaDomino.isBlank() ? "" : alertaDomino + "\n\n";
            return prefix + base + contencaoJarvisService.blocoInsightsHabito3Meses(userId);
        }

        List<InsightLinha> filtrados = new ArrayList<>();
        for (InsightLinha x : candidatos) {
            if (filtrados.size() >= 3) {
                break;
            }
            if (jarvisFeedbackService.feedbackNegativoAtivoParaCategoria(userId, x.chaveGrupo())) {
                continue;
            }
            filtrados.add(x);
        }
        if (filtrados.isEmpty()) {
            String prefix = alertaDomino.isBlank() ? "" : alertaDomino + "\n\n";
            return prefix + "Neste ciclo, *protocolos recalibrados* após feedback recente suprimiram sugestões destas categorias. "
                + "Quando o período expirar, o Sentinela reativa as análises."
                + contencaoJarvisService.blocoInsightsHabito3Meses(userId);
        }

        StringBuilder sb = new StringBuilder();
        if (!alertaDomino.isBlank()) {
            sb.append(alertaDomino).append("\n\n");
        }
        sb.append("📊 *Recorrência (últimos 90 dias)*\n\n");
        for (InsightLinha x : filtrados) {
            FinanceInsightProfileClassifier.Perfil perfil =
                FinanceInsightProfileClassifier.perfilPorDescricao(x.nomeExibicao);
            int impactoScore = scoreService.estimarPerdaSimulacao(
                x.media.multiply(new BigDecimal("0.08")).setScale(2, RoundingMode.HALF_UP));
            impactoScore = BigDecimal.valueOf(impactoScore)
                .multiply(jarvisFeedbackService.fatorPesoSugestao30d(userId, x.chaveGrupo()))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
            if (perfil == FinanceInsightProfileClassifier.Perfil.ASSINATURA_SERVICO) {
                sb.append("• ⚠️ Senhor, detectei uma elevação na assinatura de *")
                    .append(x.nomeExibicao)
                    .append("*. O valor típico mensal está em *")
                    .append(BRL.format(x.media))
                    .append("* (")
                    .append(x.mesesDistintos)
                    .append(" meses com estabilidade ±22%). Deseja *cancelar* este protocolo?");
                sb.append("\n_Nunca trate posto, mercado ou restaurante como assinatura — use alerta de consumo._\n");
                String mem = correlacaoMemoriaLongoPrazo(userId, x, true);
                if (!mem.isBlank()) {
                    sb.append(mem).append('\n');
                }
            } else {
                sb.append("• ⛽ Senhor, seus gastos em *")
                    .append(x.nomeExibicao)
                    .append("* seguem padrão de consumo variável — média *")
                    .append(BRL.format(x.media))
                    .append("* em ")
                    .append(x.ocorrencias)
                    .append(" ocorrências. O impacto projetado no seu Score é de até *")
                    .append(impactoScore)
                    .append("* pontos se o ritmo se estender.\n");
                String mem = correlacaoMemoriaLongoPrazo(userId, x, false);
                if (!mem.isBlank()) {
                    sb.append(mem).append('\n');
                }
            }
        }
        sb.append("\n_Dica: insights só aparecem quando você pergunta — não envio alertas automáticos._");
        sb.append(contencaoJarvisService.blocoInsightsHabito3Meses(userId));
        log.info("[INSIGHT-LOG] Retornando {} sugestões userId={}", filtrados.size(), userId);
        String out = sb.toString().trim();
        cerebroSemanticoAsync.registrarAposInsightsFinanceiros(userId, out);
        return out;
    }

    private String montarAlertaGatilhoHabito2h(Long userId) {
        LocalDateTime lim = LocalDateTime.now().minusHours(2);
        List<Transacao> recent = transacaoRepository.findDespesasConfirmadasDesde(userId, lim);
        for (Transacao t : recent) {
            if (t.getDataTransacao() == null) {
                continue;
            }
            Optional<GatilhoHabitoDeteccaoDTO> det = Optional.empty();
            if (t.getCategoria() != null && t.getCategoria().getNome() != null) {
                det = cerebroSemanticoService.detectarGatilhoHabito(userId, t.getCategoria().getNome());
            }
            if (det.isEmpty()) {
                det = cerebroSemanticoService.detectarGatilhoHabito(userId, t.getDescricao());
            }
            if (det.isPresent()) {
                GatilhoHabitoDeteccaoDTO g = det.get();
                return "⚠️ Senhor, detectei o gatilho *" + g.getGatilhoRotulo() + "*. O histórico indica *"
                    + g.getProbabilidadePercentual() + "%* de chance de um gasto subsequente em *"
                    + g.getAlvoRotulo() + "*. Recomendo cautela para preservar o *Escudo de Energia*.";
            }
        }
        return "";
    }

    private String correlacaoMemoriaLongoPrazo(Long userId, InsightLinha x, boolean elevacaoAssinatura) {
        String voc = jarvisProtocolService.resolveVocative(userId, usuarioRepository);
        String consulta = elevacaoAssinatura
            ? "Elevação ou assinatura com padrão de custo em " + x.nomeExibicao
            : "Padrão de gasto variável ou aumento de consumo em " + x.nomeExibicao;
        List<MemoriaSemanticaSimilaridadeDTO> hits = cerebroSemanticoService.buscarTop3Similares(userId, consulta);
        if (!hits.isEmpty() && log.isDebugEnabled()) {
            MemoriaSemanticaSimilaridadeDTO top = hits.get(0);
            int simDbg = top.getSimilaridadePercentual();
            String protocoloSugerido = simDbg >= 72
                ? (elevacaoAssinatura ? "PSI-MEM-ASSIN" : "PSI-MEM-VARI")
                : "NENHUM";
            log.debug(
                "[J.A.R.V.I.S.] Memória Semântica consultada. Hit de similaridade: {}%. Protocolo Sugerido: {}",
                simDbg,
                protocoloSugerido);
        }
        if (hits.isEmpty()) {
            return "";
        }
        MemoriaSemanticaSimilaridadeDTO h = hits.get(0);
        if (h.getSimilaridadePercentual() < 72) {
            return "";
        }
        String periodo = h.getDataRegistro() != null ? h.getDataRegistro().format(MES_ANO_PT) : "—";
        String ctxVelho = h.getContexto() == null ? "(sem detalhe textual)" : h.getContexto().trim();
        if (ctxVelho.length() > 220) {
            ctxVelho = ctxVelho.substring(0, 217) + "...";
        }
        int pct = x.pctAumentoVsMedia;
        int sim = h.getSimilaridadePercentual();
        return "_"
            + voc + ", o *Sentinela* registrou um *delta* de cerca de *" + pct + "%* no vetor *" + x.nomeExibicao + "*. "
            + "Cruzando com a memória semântica, a assinatura comportamental é *" + sim + "%* alinhada ao ciclo *"
            + periodo + "*. Registro tático: _" + ctxVelho + "._ "
            + "Deseja reativar o *protocolo de contenção* daquele período para preservar o *Escudo de Energia*?_";
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

    private record InsightLinha(
        String nomeExibicao,
        BigDecimal media,
        int mesesDistintos,
        int ocorrencias,
        int pctAumentoVsMedia,
        String chaveGrupo
    ) {
    }
}
