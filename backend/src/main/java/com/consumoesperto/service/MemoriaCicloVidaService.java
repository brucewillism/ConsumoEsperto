package com.consumoesperto.service;

import com.consumoesperto.config.MemoriaJarvisProperties;
import com.consumoesperto.model.MemoriaCategoriaOrigem;
import com.consumoesperto.model.MemoriaMetadados;
import com.consumoesperto.model.MemoriaTipo;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.util.AppTimeZone;
import com.consumoesperto.util.FinanceTextoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ciclo de vida da memória J.A.R.V.I.S. (Blocos 5 e 7):
 * expiração/decaimento, consolidação em lote e memórias derivadas dos dados
 * (RESUMO_MENSAL, EVENTO_SAZONAL, mudança de comportamento).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemoriaCicloVidaService {

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    private static final int MAX_CLUSTERS_POR_USUARIO = 3;

    /** Mesmo padrão do contexto gravado pelo HabitDominoService: «gatilho» … «alvo» … observado N vezes. */
    private static final java.util.regex.Pattern HABITO_SEQUENCIA = java.util.regex.Pattern.compile(
        "«([^»]+)».*?«([^»]+)».*?observado\\s+(\\d+)\\s*vezes",
        java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);

    private final JdbcTemplate jdbcTemplate;
    private final MemoriaJarvisProperties memoriaProps;
    private final CerebroSemanticoService cerebroSemanticoService;
    private final HabitDominoService habitDominoService;
    private final OpenAiService openAiService;
    private final UsuarioRepository usuarioRepository;
    private final TransacaoRepository transacaoRepository;

    // ------------------------------------------------------------------
    // 5.2 — Expiração diária + decaimento mensal
    // ------------------------------------------------------------------

    /** Arquiva PLANO_FUTURO com validade vencida (ex.: «cirurgia em julho» expira em agosto). */
    @Scheduled(cron = "0 30 4 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public void expirarPlanosVencidos() {
        try {
            int n = jdbcTemplate.update(
                "UPDATE memoria_semantica_jarvis SET status = 'ARQUIVADA' "
                    + "WHERE status = 'ATIVA' AND validade IS NOT NULL AND validade <= ?",
                AppTimeZone.hoje());
            if (n > 0) {
                log.info("[MEMORIA] {} plano(s) futuro(s) expirado(s) arquivado(s).", n);
            }
        } catch (Exception e) {
            log.warn("Expiração de memórias falhou: {}", e.getMessage());
        }
    }

    /**
     * Decaimento mensal (dia 1): memórias INFERIDO sem reforço há 30+ dias perdem confiança;
     * abaixo do piso, ARQUIVADA (o «hábito fantasma» sai do RAG sozinho).
     * PREFERENCIA e anotações do usuário não decaem.
     */
    @Scheduled(cron = "0 40 4 1 * *", zone = "America/Sao_Paulo")
    @Transactional
    public void decairConfiancaSemReforco() {
        try {
            int decaidas = jdbcTemplate.update(
                "UPDATE memoria_semantica_jarvis SET confianca = GREATEST(0, confianca - ?) "
                    + "WHERE status = 'ATIVA' AND origem = 'INFERIDO' AND tipo <> 'PREFERENCIA' "
                    + "AND COALESCE(confirmada_usuario, FALSE) = FALSE "
                    + "AND COALESCE(ultimo_reforco_em, data_registro) < NOW() - INTERVAL '30 days'",
                memoriaProps.getDecaimentoConfiancaMensal());
            int arquivadas = jdbcTemplate.update(
                "UPDATE memoria_semantica_jarvis SET status = 'ARQUIVADA' "
                    + "WHERE status = 'ATIVA' AND origem = 'INFERIDO' AND confianca < ?",
                memoriaProps.getConfiancaPiso());
            if (decaidas > 0 || arquivadas > 0) {
                log.info("[MEMORIA] Decaimento mensal: {} memória(s) decaída(s), {} arquivada(s) abaixo do piso.",
                    decaidas, arquivadas);
            }
        } catch (Exception e) {
            log.warn("Decaimento de memórias falhou: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Re-validação de hábitos antigos sem evidência (rodada de ajustes finos, item 1)
    // ------------------------------------------------------------------

    /**
     * Hábitos criados ANTES da coluna {@code transacoes_evidencia} não têm caminho de invalidação
     * retroativa. Este job re-deriva a evidência re-rodando a mesma detecção do efeito dominó
     * contra as transações vivas e confirmadas atuais:
     * suporte suficiente → backfill da evidência; sem suporte → INVALIDADA.
     * Idempotente: na 2ª execução nenhum HABITO ATIVA fica sem evidência, então nada muda.
     */
    @Scheduled(cron = "0 50 4 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public void revalidarHabitosSemEvidencia() {
        List<Map<String, Object>> habitos;
        try {
            habitos = jdbcTemplate.queryForList(
                "SELECT id, usuario_id, contexto FROM memoria_semantica_jarvis "
                    + "WHERE tipo = 'HABITO' AND status = 'ATIVA' "
                    + "AND (transacoes_evidencia IS NULL OR transacoes_evidencia = '')");
        } catch (Exception e) {
            log.warn("Re-validação de hábitos sem evidência falhou na leitura: {}", e.getMessage());
            return;
        }
        if (habitos.isEmpty()) {
            return;
        }
        int backfilled = 0;
        int invalidados = 0;
        for (Map<String, Object> row : habitos) {
            long memId = ((Number) row.get("id")).longValue();
            long userId = ((Number) row.get("usuario_id")).longValue();
            String contexto = (String) row.get("contexto");
            try {
                if (revalidarHabito(memId, userId, contexto)) {
                    backfilled++;
                } else {
                    invalidados++;
                }
            } catch (Exception e) {
                log.warn("Re-validação do hábito memId={} userId={} falhou: {}", memId, userId, e.getMessage());
            }
        }
        log.info("[MEMORIA] Re-validação de hábitos sem evidência: {} backfilled, {} invalidado(s).",
            backfilled, invalidados);
    }

    /** @return {@code true} se a evidência foi re-derivada (backfill); {@code false} se INVALIDADA. */
    private boolean revalidarHabito(long memId, long userId, String contexto) {
        HabitDominoService.SuporteDerivado suporte = null;
        var m = contexto != null ? HABITO_SEQUENCIA.matcher(contexto) : null;
        if (m != null && m.find()) {
            String keyGatilho = FinanceTextoUtil.chaveAgrupamento(m.group(1).trim());
            String keyAlvo = FinanceTextoUtil.chaveAgrupamento(m.group(2).trim());
            suporte = habitDominoService.derivarSuporte(userId, keyGatilho, keyAlvo);
        }
        if (suporte != null
            && habitDominoService.suporteSuficiente(suporte.ocorrencias(), suporte.diasDistintos())) {
            String csv = suporte.evidencia().stream().map(String::valueOf)
                .reduce((a, b) -> a + "," + b).orElse(null);
            jdbcTemplate.update(
                "UPDATE memoria_semantica_jarvis SET transacoes_evidencia = ? WHERE id = ?",
                csv, memId);
            return true;
        }
        // Contexto irreconhecível ou evidência inexistente/insuficiente nas transações vivas:
        // o hábito nunca atingiu o critério novo ou perdeu o lastro — sai do RAG.
        jdbcTemplate.update(
            "UPDATE memoria_semantica_jarvis SET status = 'INVALIDADA' WHERE id = ?", memId);
        return false;
    }

    // ------------------------------------------------------------------
    // 5.1 — Consolidação semanal em lote (clusters por similaridade)
    // ------------------------------------------------------------------

    /** Agrupa memórias ATIVAS do mesmo tema; acima do limiar, 1 resumo denso + originais ARQUIVADAS. */
    @Scheduled(cron = "0 10 5 * * MON", zone = "America/Sao_Paulo")
    public void consolidarMemoriasSemelhantes() {
        for (Usuario u : usuarioRepository.findAll()) {
            try {
                consolidarParaUsuario(u.getId());
            } catch (Exception e) {
                log.warn("Consolidação de memórias falhou userId={}: {}", u.getId(), e.getMessage());
            }
        }
    }

    @Transactional
    public void consolidarParaUsuario(Long usuarioId) {
        List<Map<String, Object>> ativas = jdbcTemplate.queryForList(
            "SELECT id, tipo FROM memoria_semantica_jarvis "
                + "WHERE usuario_id = ? AND status = 'ATIVA' AND embedding IS NOT NULL "
                + "AND tipo IN ('FATO','HABITO','PREFERENCIA') ORDER BY data_registro ASC",
            usuarioId);
        if (ativas.size() < memoriaProps.getConsolidacaoMinCluster()) {
            return;
        }
        double distMax = 1.0 - memoriaProps.getConsolidacaoSimilaridade();
        Set<Long> jaAgrupadas = new HashSet<>();
        int clustersProcessados = 0;
        for (Map<String, Object> seed : ativas) {
            if (clustersProcessados >= MAX_CLUSTERS_POR_USUARIO) {
                break;
            }
            Long seedId = ((Number) seed.get("id")).longValue();
            String tipo = String.valueOf(seed.get("tipo"));
            if (jaAgrupadas.contains(seedId)) {
                continue;
            }
            List<Map<String, Object>> cluster = jdbcTemplate.queryForList(
                "SELECT id, contexto FROM memoria_semantica_jarvis "
                    + "WHERE usuario_id = ? AND status = 'ATIVA' AND tipo = ? AND embedding IS NOT NULL "
                    + "AND (embedding <=> (SELECT embedding FROM memoria_semantica_jarvis WHERE id = ?)) <= ?",
                usuarioId, tipo, seedId, distMax);
            List<Long> ids = new ArrayList<>();
            List<String> contextos = new ArrayList<>();
            for (Map<String, Object> m : cluster) {
                Long id = ((Number) m.get("id")).longValue();
                if (jaAgrupadas.contains(id)) {
                    continue;
                }
                ids.add(id);
                contextos.add(String.valueOf(m.get("contexto")));
            }
            if (ids.size() < memoriaProps.getConsolidacaoMinCluster()) {
                continue;
            }
            jaAgrupadas.addAll(ids);
            String resumo = resumirCluster(usuarioId, contextos);
            if (resumo == null || resumo.isBlank()) {
                continue;
            }
            MemoriaTipo mt = tipoSeguro(tipo);
            cerebroSemanticoService.gravarMemoria(
                usuarioId,
                resumo,
                mt == MemoriaTipo.HABITO ? MemoriaCategoriaOrigem.HABITO : MemoriaCategoriaOrigem.FINANCAS,
                MemoriaMetadados.sistema(mt));
            for (Long id : ids) {
                jdbcTemplate.update(
                    "UPDATE memoria_semantica_jarvis SET status = 'ARQUIVADA' WHERE id = ? AND usuario_id = ?",
                    id, usuarioId);
            }
            clustersProcessados++;
            log.info("[MEMORIA] Cluster consolidado userId={}: {} memórias → 1 resumo ({}).",
                usuarioId, ids.size(), tipo);
        }
    }

    /** Uma chamada de LLM por cluster (batelada); fallback determinístico se a IA falhar. */
    private String resumirCluster(Long usuarioId, List<String> contextos) {
        StringBuilder corpo = new StringBuilder();
        for (String c : contextos) {
            corpo.append("- ").append(c == null ? "" : c.replace('\n', ' ').trim()).append('\n');
        }
        try {
            String texto = openAiService.gerarTexto(
                usuarioId,
                "Consolide as memórias abaixo (mesmo tema, mesmo usuário) em UM único parágrafo denso e factual "
                    + "em português, máximo 400 caracteres, preservando valores e padrões relevantes. "
                    + "Retorne JSON {\"texto\":\"...\"}.",
                corpo.toString(),
                "");
            if (texto != null && !texto.isBlank()) {
                return "Consolidado: " + texto.trim();
            }
        } catch (Exception e) {
            log.debug("LLM de consolidação indisponível userId={}: {}", usuarioId, e.getMessage());
        }
        String primeiro = contextos.get(0) == null ? "" : contextos.get(0).replace('\n', ' ').trim();
        if (primeiro.length() > 300) {
            primeiro = primeiro.substring(0, 297) + "...";
        }
        return "Consolidado (" + contextos.size() + " registros do mesmo tema): " + primeiro;
    }

    private static MemoriaTipo tipoSeguro(String raw) {
        try {
            return MemoriaTipo.valueOf(raw);
        } catch (Exception e) {
            return MemoriaTipo.FATO;
        }
    }

    // ------------------------------------------------------------------
    // Bloco 7 — memórias derivadas dos dados (origem SISTEMA)
    // ------------------------------------------------------------------

    /** Dia 1: RESUMO_MENSAL do mês fechado + EVENTO_SAZONAL + mudança de comportamento. */
    @Scheduled(cron = "0 10 8 1 * *", zone = "America/Sao_Paulo")
    public void gerarMemoriasDerivadasMensais() {
        YearMonth fechado = AppTimeZone.mesAtual().minusMonths(1);
        for (Usuario u : usuarioRepository.findAll()) {
            try {
                gerarResumoMensal(u.getId(), fechado);
                gerarEventosSazonais(u.getId(), fechado);
                gerarMudancaComportamentoAssinaturas(u.getId(), fechado);
            } catch (Exception e) {
                log.warn("Memórias derivadas falharam userId={} mês={}: {}", u.getId(), fechado, e.getMessage());
            }
        }
    }

    @Transactional
    public void gerarResumoMensal(Long usuarioId, YearMonth mes) {
        Integer existente = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM memoria_semantica_jarvis WHERE usuario_id = ? AND tipo = 'RESUMO_MENSAL' "
                + "AND mes_alvo = ? AND ano_alvo = ?",
            Integer.class, usuarioId, mes.getMonthValue(), mes.getYear());
        if (existente != null && existente > 0) {
            return;
        }
        List<Transacao> despesas = despesasConfirmadasDoMes(usuarioId, mes);
        if (despesas.isEmpty()) {
            return;
        }
        BigDecimal total = despesas.stream()
            .map(t -> t.getValor() != null ? t.getValor() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        String categoriaDominante = categoriaDominante(despesas);
        BigDecimal media3m = mediaDespesas3MesesAnteriores(usuarioId, mes);
        String comparativo = "";
        if (media3m.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal pct = total.subtract(media3m)
                .multiply(new BigDecimal("100"))
                .divide(media3m, 0, RoundingMode.HALF_UP);
            comparativo = ", " + (pct.signum() >= 0 ? "+" : "") + pct + "% vs média dos 3 meses anteriores";
        }
        String nomeMes = Month.of(mes.getMonthValue())
            .getDisplayName(TextStyle.FULL, new Locale("pt", "BR"));
        String contexto = "Resumo mensal " + nomeMes + "/" + mes.getYear() + ": gastou " + BRL.format(total)
            + ", categoria dominante " + categoriaDominante + comparativo + ".";
        cerebroSemanticoService.gravarMemoria(
            usuarioId,
            contexto,
            MemoriaCategoriaOrigem.FINANCAS,
            MemoriaMetadados.sistema(MemoriaTipo.RESUMO_MENSAL)
                .comValor(total)
                .comCategoria(categoriaDominante)
                .comAlvo(mes.getMonthValue(), mes.getYear()));
    }

    /**
     * EVENTO_SAZONAL: gasto grande do mês fechado que também ocorreu no mesmo mês do ano anterior
     * (IPVA, matrícula, seguro). Só lembra — a provisão continua a cargo do Sentinela (sem duplicar).
     */
    @Transactional
    public void gerarEventosSazonais(Long usuarioId, YearMonth mes) {
        List<Transacao> atuais = despesasConfirmadasDoMes(usuarioId, mes);
        if (atuais.isEmpty()) {
            return;
        }
        YearMonth anoAnterior = mes.minusYears(1);
        Set<String> chavesAnoAnterior = new HashSet<>();
        for (Transacao t : despesasConfirmadasDoMes(usuarioId, anoAnterior)) {
            BigDecimal v = t.getValor() != null ? t.getValor() : BigDecimal.ZERO;
            if (v.compareTo(new BigDecimal("300")) >= 0) {
                chavesAnoAnterior.add(FinanceTextoUtil.chaveAgrupamento(t.getDescricao()));
            }
        }
        if (chavesAnoAnterior.isEmpty()) {
            return;
        }
        String nomeMes = Month.of(mes.getMonthValue())
            .getDisplayName(TextStyle.FULL, new Locale("pt", "BR"));
        int gravadas = 0;
        for (Transacao t : atuais) {
            if (gravadas >= 3) {
                break;
            }
            BigDecimal v = t.getValor() != null ? t.getValor() : BigDecimal.ZERO;
            if (v.compareTo(new BigDecimal("350")) < 0) {
                continue;
            }
            String chave = FinanceTextoUtil.chaveAgrupamento(t.getDescricao());
            if ("_vazio_".equals(chave) || !chavesAnoAnterior.contains(chave)) {
                continue;
            }
            String rotulo = FinanceTextoUtil.rotuloAmigavel(t.getDescricao());
            String contexto = "Evento sazonal anual: gasto recorrente em «" + rotulo + "» todo mês de "
                + nomeMes + " (~" + BRL.format(v) + "; observado em " + anoAnterior.getYear()
                + " e " + mes.getYear() + ").";
            cerebroSemanticoService.gravarMemoria(
                usuarioId,
                contexto,
                MemoriaCategoriaOrigem.FINANCAS,
                MemoriaMetadados.sistema(MemoriaTipo.EVENTO_SAZONAL)
                    .comValor(v)
                    .comAlvo(mes.getMonthValue(), null)
                    .comEvidencia(List.of(t.getId())));
            gravadas++;
        }
    }

    /** Mudança de comportamento: gastos recorrentes/assinaturas do trimestre subiram acima do limiar. */
    @Transactional
    public void gerarMudancaComportamentoAssinaturas(Long usuarioId, YearMonth mesFechado) {
        BigDecimal triAtual = somaRecorrentesTrimestre(usuarioId, mesFechado.minusMonths(2), mesFechado);
        BigDecimal triAnterior = somaRecorrentesTrimestre(usuarioId, mesFechado.minusMonths(5), mesFechado.minusMonths(3));
        if (triAnterior.compareTo(new BigDecimal("50")) < 0 || triAtual.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal pct = triAtual.subtract(triAnterior)
            .multiply(new BigDecimal("100"))
            .divide(triAnterior, 0, RoundingMode.HALF_UP);
        if (pct.compareTo(new BigDecimal("30")) < 0) {
            return;
        }
        String contexto = "Mudança de comportamento: gastos recorrentes/assinaturas subiram " + pct
            + "% no trimestre encerrado em "
            + Month.of(mesFechado.getMonthValue()).getDisplayName(TextStyle.FULL, new Locale("pt", "BR"))
            + "/" + mesFechado.getYear() + " (" + BRL.format(triAnterior) + " → " + BRL.format(triAtual) + ").";
        cerebroSemanticoService.gravarMemoria(
            usuarioId,
            contexto,
            MemoriaCategoriaOrigem.FINANCAS,
            MemoriaMetadados.sistema(MemoriaTipo.FATO).comValor(triAtual));
    }

    // ------------------------------------------------------------------
    // Helpers de dados
    // ------------------------------------------------------------------

    private List<Transacao> despesasConfirmadasDoMes(Long usuarioId, YearMonth mes) {
        LocalDateTime ini = mes.atDay(1).atStartOfDay();
        LocalDateTime fim = mes.atEndOfMonth().atTime(23, 59, 59);
        return transacaoRepository.findByUsuarioIdAndTipoAndPeriodo(
                usuarioId, Transacao.TipoTransacao.DESPESA, ini, fim).stream()
            .filter(t -> t.getStatusConferencia() == Transacao.StatusConferencia.CONFIRMADA)
            .toList();
    }

    private static String categoriaDominante(List<Transacao> despesas) {
        Map<String, BigDecimal> porCategoria = new HashMap<>();
        for (Transacao t : despesas) {
            String nome = t.getCategoria() != null && t.getCategoria().getNome() != null
                ? t.getCategoria().getNome()
                : "Sem categoria";
            porCategoria.merge(nome, t.getValor() != null ? t.getValor() : BigDecimal.ZERO, BigDecimal::add);
        }
        return porCategoria.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("Sem categoria");
    }

    private BigDecimal mediaDespesas3MesesAnteriores(Long usuarioId, YearMonth mes) {
        BigDecimal soma = BigDecimal.ZERO;
        int meses = 0;
        for (int i = 1; i <= 3; i++) {
            YearMonth m = mes.minusMonths(i);
            BigDecimal totalMes = despesasConfirmadasDoMes(usuarioId, m).stream()
                .map(t -> t.getValor() != null ? t.getValor() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalMes.compareTo(BigDecimal.ZERO) > 0) {
                soma = soma.add(totalMes);
                meses++;
            }
        }
        if (meses == 0) {
            return BigDecimal.ZERO;
        }
        return soma.divide(BigDecimal.valueOf(meses), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal somaRecorrentesTrimestre(Long usuarioId, YearMonth de, YearMonth ate) {
        BigDecimal soma = BigDecimal.ZERO;
        for (YearMonth m = de; !m.isAfter(ate); m = m.plusMonths(1)) {
            soma = soma.add(despesasConfirmadasDoMes(usuarioId, m).stream()
                .filter(Transacao::isRecorrente)
                .map(t -> t.getValor() != null ? t.getValor() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        return soma.setScale(2, RoundingMode.HALF_UP);
    }
}
