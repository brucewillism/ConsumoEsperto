package com.consumoesperto.service;

import com.consumoesperto.config.MemoriaJarvisProperties;
import com.consumoesperto.dto.GatilhoHabitoDeteccaoDTO;
import com.consumoesperto.dto.MemoriaSemanticaSimilaridadeDTO;
import com.consumoesperto.dto.MemoriaSemanticaTimelineItemDTO;
import com.consumoesperto.model.MemoriaCategoriaOrigem;
import com.consumoesperto.model.MemoriaMetadados;
import com.consumoesperto.model.MemoriaOrigem;
import com.consumoesperto.model.MemoriaStatus;
import com.consumoesperto.model.MemoriaTipo;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.util.FinanceTextoUtil;
import com.consumoesperto.util.PgVectorJdbcHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PGobject;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Persistência e consulta por similaridade (pgvector) da memória J.A.R.V.I.S.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CerebroSemanticoService {

    private static final int DIM_VETOR = 1536;
    private static final int TOP_K = 3;
    private static final Pattern HABITO_SEQUENCIA =
        Pattern.compile("«([^»]+)».*?«([^»]+)».*?observado\\s+(\\d+)\\s*vezes", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final JdbcTemplate jdbcTemplate;
    private final OpenAiService openAiService;
    private final TransacaoRepository transacaoRepository;
    private final MemoriaJarvisProperties memoriaProps;

    private volatile boolean loggedMemoriaUnavailable;
    /** {@code null} = ainda não lido {@code information_schema}; depois memoiza se existe coluna {@code embedding vector}. */
    private volatile Boolean cachedMemoriaVectorSimilarityOps;

    private boolean memoriaEmbeddingSupportsPgvectorOperators() {
        Boolean c = cachedMemoriaVectorSimilarityOps;
        if (c != null) {
            return c;
        }
        synchronized (this) {
            c = cachedMemoriaVectorSimilarityOps;
            if (c != null) {
                return c;
            }
            boolean yes = PgVectorJdbcHelper.isVectorEmbeddingColumn(
                jdbcTemplate, "public", "memoria_semantica_jarvis", "embedding");
            cachedMemoriaVectorSimilarityOps = yes;
            return yes;
        }
    }

    private static boolean isMemoriaSemanticsUnavailable(DataAccessException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String m = t.getMessage();
            if (m == null) {
                continue;
            }
            if (m.contains("memoria_semantica_jarvis") && m.contains("does not exist")) {
                return true;
            }
        }
        return false;
    }

    private void logMemoriaUnavailableOnce(Throwable e) {
        if (loggedMemoriaUnavailable) {
            return;
        }
        loggedMemoriaUnavailable = true;
        log.warn(
            "Memória semântica J.A.R.V.I.S. indisponível (tabela ausente ou sem pgvector). "
                + "Aplique patch de BD ou rode SchemaAutoPatch ao subir — detalhes: {}",
            e.getMessage() != null ? e.getMessage() : e.toString());
    }

    /** Compatibilidade: gravação sem metadados vira FATO/SISTEMA com defaults. */
    @Transactional
    public void gravarMemoria(Long usuarioId, String contexto, MemoriaCategoriaOrigem categoria) {
        gravarMemoria(usuarioId, contexto, categoria, MemoriaMetadados.sistema(MemoriaTipo.FATO));
    }

    /**
     * Gravação estruturada com higiene: dedupe semântico (reforça em vez de duplicar) e
     * superação de contradições (PREFERENCIA/PLANO_FUTURO mais recente vence a antiga).
     */
    @Transactional
    public void gravarMemoria(Long usuarioId, String contexto, MemoriaCategoriaOrigem categoria, MemoriaMetadados meta) {
        if (usuarioId == null || contexto == null || contexto.isBlank() || categoria == null || meta == null) {
            return;
        }
        String ctx = contexto.trim();
        PGobject vec = null;
        Optional<float[]> emb = openAiService.tryCreateEmbedding(ctx, usuarioId);
        if (memoriaEmbeddingSupportsPgvectorOperators() && emb.isPresent()) {
            float[] f = emb.get();
            if (f.length != DIM_VETOR) {
                log.warn(
                    "Embedding com dimensão {} (esperado {}); gravação sem vetor.",
                    f.length,
                    DIM_VETOR);
            } else {
                vec = pgVector(f);
            }
        }
        try {
            if (reforcarSeQuaseIdentica(usuarioId, ctx, vec, meta)) {
                return;
            }
            Long novoId = jdbcTemplate.queryForObject(
                "INSERT INTO memoria_semantica_jarvis "
                    + "(usuario_id, contexto, embedding, categoria_origem, tipo, status, origem, confianca, validade, "
                    + "valor, categoria, mes_alvo, ano_alvo, contador_reforco, ultimo_reforco_em, transacoes_evidencia) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,1,NOW(),?) RETURNING id",
                Long.class,
                usuarioId,
                ctx,
                vec,
                categoria.name(),
                nomeTipo(meta),
                MemoriaStatus.ATIVA.name(),
                meta.origem() != null ? meta.origem().name() : MemoriaOrigem.SISTEMA.name(),
                meta.confianca() != null ? meta.confianca().setScale(2, RoundingMode.HALF_UP) : new BigDecimal("0.50"),
                meta.validade(),
                meta.valor() != null ? meta.valor().setScale(2, RoundingMode.HALF_UP) : null,
                meta.categoria(),
                meta.mesAlvo(),
                meta.anoAlvo(),
                evidenciaCsv(meta.transacoesEvidencia()));
            superarContradicoes(usuarioId, vec, meta, novoId);
        } catch (DataAccessException dex) {
            if (isMemoriaSemanticsUnavailable(dex)) {
                logMemoriaUnavailableOnce(dex);
                return;
            }
            throw dex;
        }
    }

    private static String nomeTipo(MemoriaMetadados meta) {
        return meta.tipo() != null ? meta.tipo().name() : MemoriaTipo.FATO.name();
    }

    private static String evidenciaCsv(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * Dedupe (1.2): memória quase idêntica ATIVA existente → reforço (contador+confiança+evidência), sem nova linha.
     * Com vetor usa similaridade; sem vetor cai no match textual exato.
     */
    private boolean reforcarSeQuaseIdentica(Long usuarioId, String ctx, PGobject vec, MemoriaMetadados meta) {
        Long idExistente = null;
        if (vec != null) {
            double distMax = 1.0 - memoriaProps.getDedupeSimilaridadeMinima();
            List<Long> ids = jdbcTemplate.query(
                con -> {
                    var ps = con.prepareStatement(
                        "SELECT id FROM memoria_semantica_jarvis "
                            + "WHERE usuario_id = ? AND status = 'ATIVA' AND tipo = ? AND embedding IS NOT NULL "
                            + "AND (embedding <=> (?::vector)) <= ? "
                            + "ORDER BY embedding <=> (?::vector) LIMIT 1");
                    ps.setLong(1, usuarioId);
                    ps.setString(2, nomeTipo(meta));
                    ps.setObject(3, vec);
                    ps.setDouble(4, distMax);
                    ps.setObject(5, vec);
                    return ps;
                },
                (rs, rn) -> rs.getLong(1));
            idExistente = ids.isEmpty() ? null : ids.get(0);
        }
        if (idExistente == null) {
            List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM memoria_semantica_jarvis WHERE usuario_id = ? AND status = 'ATIVA' AND contexto = ? LIMIT 1",
                (rs, rn) -> rs.getLong(1), usuarioId, ctx);
            idExistente = ids.isEmpty() ? null : ids.get(0);
        }
        if (idExistente == null) {
            return false;
        }
        String evid = evidenciaCsv(meta.transacoesEvidencia());
        jdbcTemplate.update(
            "UPDATE memoria_semantica_jarvis SET "
                + "contador_reforco = contador_reforco + 1, "
                + "ultimo_reforco_em = NOW(), "
                + "confianca = LEAST(0.95, confianca + 0.05), "
                // ?::text — sem o cast o Postgres não infere o tipo do parâmetro dentro do CASE
                + "transacoes_evidencia = CASE WHEN ?::text IS NULL THEN transacoes_evidencia "
                + "  WHEN transacoes_evidencia IS NULL THEN ?::text ELSE transacoes_evidencia || ',' || ?::text END "
                + "WHERE id = ? AND usuario_id = ?",
            evid, evid, evid, idExistente, usuarioId);
        log.debug("[MEMORIA] Reforço em vez de duplicata id={} userId={}", idExistente, usuarioId);
        return true;
    }

    /**
     * Contradições (4.2): PREFERENCIA/PLANO_FUTURO novo com alta similaridade a um antigo do mesmo tipo
     * marca o antigo como SUPERADA (a mais recente prevalece; histórico preservado fora do RAG).
     */
    private void superarContradicoes(Long usuarioId, PGobject vec, MemoriaMetadados meta, Long novaMemoriaId) {
        MemoriaTipo tipo = meta.tipo();
        if (vec == null || tipo == null || novaMemoriaId == null
            || (tipo != MemoriaTipo.PREFERENCIA && tipo != MemoriaTipo.PLANO_FUTURO)) {
            return;
        }
        // Compatibilidade de metadados: mesma categoria (ambas preenchidas) usa o limiar normal;
        // se qualquer uma não tiver categoria, exige limiar mais alto; categorias diferentes nunca superam.
        double distMesmaCategoria = 1.0 - memoriaProps.getSuperacaoSimilaridadeMinima();
        double distSemCategoria = 1.0 - memoriaProps.getSuperacaoSimilaridadeSemCategoria();
        String categoriaNova = meta.categoria();
        List<Long> superadas = jdbcTemplate.query(
            con -> {
                var ps = con.prepareStatement(
                    "SELECT id FROM memoria_semantica_jarvis "
                        + "WHERE usuario_id = ? AND status = 'ATIVA' AND tipo = ? AND embedding IS NOT NULL "
                        + "AND id <> ? AND restaurada_em IS NULL "
                        + "AND ((categoria IS NOT NULL AND ?::varchar IS NOT NULL AND categoria = ?::varchar "
                        + "      AND (embedding <=> (?::vector)) <= ?) "
                        + "  OR ((categoria IS NULL OR ?::varchar IS NULL) "
                        + "      AND (embedding <=> (?::vector)) <= ?))");
                ps.setLong(1, usuarioId);
                ps.setString(2, tipo.name());
                ps.setLong(3, novaMemoriaId);
                ps.setString(4, categoriaNova);
                ps.setString(5, categoriaNova);
                ps.setObject(6, vec);
                ps.setDouble(7, distMesmaCategoria);
                ps.setString(8, categoriaNova);
                ps.setObject(9, vec);
                ps.setDouble(10, distSemCategoria);
                return ps;
            },
            (rs, rn) -> rs.getLong(1));
        for (Long id : superadas) {
            jdbcTemplate.update(
                "UPDATE memoria_semantica_jarvis SET status = 'SUPERADA', superada_por_id = ? "
                    + "WHERE id = ? AND usuario_id = ?",
                novaMemoriaId, id, usuarioId);
        }
        if (!superadas.isEmpty()) {
            log.info("[MEMORIA] {} memória(s) {} superada(s) por versão mais recente userId={}",
                superadas.size(), tipo, usuarioId);
        }
    }

    public List<MemoriaSemanticaSimilaridadeDTO> buscarTop3Similares(Long usuarioId, String textoConsulta) {
        return buscarSimilaresAtivas(usuarioId, textoConsulta, TOP_K);
    }

    /**
     * Busca híbrida (4.1): score = similaridade × recência (meia-vida configurável) × reforço,
     * filtrando {@code status = ATIVA} e limitando a K memórias (4.3).
     */
    public List<MemoriaSemanticaSimilaridadeDTO> buscarSimilaresAtivas(Long usuarioId, String textoConsulta, int k) {
        if (usuarioId == null || textoConsulta == null || textoConsulta.isBlank() || k <= 0) {
            return List.of();
        }
        Optional<float[]> query = openAiService.tryCreateEmbedding(textoConsulta.trim(), usuarioId);
        if (query.isEmpty()) {
            return List.of();
        }
        float[] q = query.get();
        if (q.length != DIM_VETOR) {
            return List.of();
        }
        if (!memoriaEmbeddingSupportsPgvectorOperators()) {
            return List.of();
        }
        int limite = Math.min(k, Math.max(1, memoriaProps.getRagLimiteContexto()));
        // decaimento exponencial: peso 0.5 quando a idade = meia-vida
        double lambdaPorDia = Math.log(2) / Math.max(1, memoriaProps.getRagMeiaVidaDias());
        PGobject probe = pgVector(q);
        String sql = "SELECT contexto, data_registro, (embedding <=> (?::vector)) AS dist, "
            + "(1 - (embedding <=> (?::vector))) "
            + " * exp(-GREATEST(0, EXTRACT(EPOCH FROM (NOW() - COALESCE(ultimo_reforco_em, data_registro)))) / 86400.0 * ?) "
            + " * (1 + LEAST(contador_reforco, 10) * 0.05) AS score "
            + "FROM memoria_semantica_jarvis "
            + "WHERE usuario_id = ? AND embedding IS NOT NULL AND status = 'ATIVA' "
            + "ORDER BY score DESC "
            + "LIMIT " + limite;
        try {
            return jdbcTemplate.query(
                connection -> {
                    var ps = connection.prepareStatement(sql);
                    ps.setObject(1, probe);
                    ps.setObject(2, probe);
                    ps.setDouble(3, lambdaPorDia);
                    ps.setLong(4, usuarioId);
                    return ps;
                },
                (rs, rowNum) -> mapSimilarRow(rs));
        } catch (DataAccessException dex) {
            if (isMemoriaSemanticsUnavailable(dex)) {
                logMemoriaUnavailableOnce(dex);
                return List.of();
            }
            throw dex;
        }
    }

    public List<String> listarContextosMemoriaNoMesCalendario(Long usuarioId, int mes, int ano) {
        if (usuarioId == null || mes < 1 || mes > 12) {
            return List.of();
        }
        String sql = "SELECT contexto FROM memoria_semantica_jarvis WHERE usuario_id = ? AND status = 'ATIVA' "
            + "AND EXTRACT(MONTH FROM data_registro) = ? AND EXTRACT(YEAR FROM data_registro) = ? "
            + "ORDER BY data_registro DESC LIMIT 80";
        try {
            return jdbcTemplate.query(sql, (rs, rn) -> rs.getString(1), usuarioId, mes, ano);
        } catch (DataAccessException dex) {
            if (isMemoriaSemanticsUnavailable(dex)) {
                logMemoriaUnavailableOnce(dex);
                return List.of();
            }
            throw dex;
        }
    }

    /**
     * União sem duplicatas: memórias do mesmo mês no ano anterior + memórias do ano corrente
     * que mencionam o mês-alvo (nome, «mês N», etc.).
     */
    public List<String> listarContextosMemoriaParaProvisaoMes(Long usuarioId, int mesAlvo, int anoAlvo) {
        if (usuarioId == null || mesAlvo < 1 || mesAlvo > 12) {
            return List.of();
        }
        LinkedHashSet<String> uniao = new LinkedHashSet<>();
        uniao.addAll(listarContextosMemoriaNoMesCalendario(usuarioId, mesAlvo, anoAlvo - 1));
        uniao.addAll(buscarMemoriasAnoCorrenteReferenciandoMes(usuarioId, mesAlvo, anoAlvo));
        return new ArrayList<>(uniao);
    }

    private List<String> buscarMemoriasAnoCorrenteReferenciandoMes(Long usuarioId, int mesAlvo, int anoAlvo) {
        List<String> termos = termosBuscaMesAlvo(mesAlvo);
        if (termos.isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder(
            "SELECT contexto FROM memoria_semantica_jarvis WHERE usuario_id = ? AND status = 'ATIVA' "
                + "AND EXTRACT(YEAR FROM data_registro) = ? AND (");
        List<Object> params = new ArrayList<>();
        params.add(usuarioId);
        params.add(anoAlvo);
        for (int i = 0; i < termos.size(); i++) {
            if (i > 0) {
                sql.append(" OR ");
            }
            sql.append("LOWER(contexto) LIKE ?");
            params.add("%" + termos.get(i).toLowerCase(Locale.ROOT) + "%");
        }
        sql.append(") ORDER BY data_registro DESC LIMIT 80");
        try {
            return jdbcTemplate.query(sql.toString(), (rs, rn) -> rs.getString(1), params.toArray());
        } catch (DataAccessException dex) {
            if (isMemoriaSemanticsUnavailable(dex)) {
                logMemoriaUnavailableOnce(dex);
                return List.of();
            }
            throw dex;
        }
    }

    private static List<String> termosBuscaMesAlvo(int mes) {
        Locale ptBr = new Locale("pt", "BR");
        String nomeMes = Month.of(mes).getDisplayName(TextStyle.FULL, ptBr).toLowerCase(Locale.ROOT);
        List<String> termos = new ArrayList<>();
        termos.add(nomeMes);
        termos.add("mes " + mes);
        termos.add("mês " + mes);
        return termos;
    }

    /**
     * Efeito dominó: verifica se o rótulo/categoria atual corresponde ao gatilho de alguma memória {@code HABITO}
     * (sequências «A» → «B» gravadas pelo hábito).
     */
    public Optional<GatilhoHabitoDeteccaoDTO> detectarGatilhoHabito(Long usuarioId, String categoriaAtual) {
        if (usuarioId == null || categoriaAtual == null || categoriaAtual.isBlank()) {
            return Optional.empty();
        }
        String keyAtual = FinanceTextoUtil.chaveAgrupamento(categoriaAtual);
        if ("_vazio_".equals(keyAtual)) {
            return Optional.empty();
        }
        String sql = "SELECT contexto FROM memoria_semantica_jarvis WHERE usuario_id = ? AND categoria_origem = 'HABITO' "
            + "AND status = 'ATIVA' ORDER BY data_registro DESC LIMIT 80";
        List<String> ctxs;
        try {
            ctxs = jdbcTemplate.query(sql, (rs, i) -> rs.getString(1), usuarioId);
        } catch (DataAccessException dex) {
            if (isMemoriaSemanticsUnavailable(dex)) {
                logMemoriaUnavailableOnce(dex);
                return Optional.empty();
            }
            throw dex;
        }
        for (String ctx : ctxs) {
            if (ctx == null || ctx.isBlank()) {
                continue;
            }
            Matcher m = HABITO_SEQUENCIA.matcher(ctx);
            if (!m.find()) {
                continue;
            }
            String rotA = m.group(1).trim();
            String rotB = m.group(2).trim();
            int observacoes = Integer.parseInt(m.group(3).trim());
            String keyGatilho = FinanceTextoUtil.chaveAgrupamento(rotA);
            if (!keyAtual.equals(keyGatilho)) {
                continue;
            }
            String keyAlvo = FinanceTextoUtil.chaveAgrupamento(rotB);
            BigDecimal mediaSegunda = mediaSegundaPernaNoHistorico(usuarioId, keyGatilho, keyAlvo);
            int prob = Math.max(85, Math.min(95, 60 + observacoes * 2));
            return Optional.of(GatilhoHabitoDeteccaoDTO.builder()
                .gatilhoRotulo(FinanceTextoUtil.rotuloAmigavel(rotA))
                .alvoRotulo(FinanceTextoUtil.rotuloAmigavel(rotB))
                .probabilidadePercentual(prob)
                .valorMedioSegundaPerna(mediaSegunda)
                .build());
        }
        return Optional.empty();
    }

    private BigDecimal mediaSegundaPernaNoHistorico(Long userId, String keyPrimeira, String keySegunda) {
        LocalDateTime iniHist = LocalDateTime.now().minusDays(400);
        List<Transacao> todas = transacaoRepository.findByUsuarioIdAndTipoTransacaoOrderByDataTransacaoDesc(
            userId, Transacao.TipoTransacao.DESPESA);
        List<Transacao> conf = todas.stream()
            .filter(x -> x.getStatusConferencia() == Transacao.StatusConferencia.CONFIRMADA)
            .filter(x -> x.getDataTransacao() != null && !x.getDataTransacao().isBefore(iniHist))
            .sorted(Comparator.comparing(Transacao::getDataTransacao))
            .toList();
        BigDecimal sum = BigDecimal.ZERO;
        int n = 0;
        for (int i = 0; i < conf.size() - 1; i++) {
            Transacao a = conf.get(i);
            Transacao b = conf.get(i + 1);
            long h = ChronoUnit.HOURS.between(a.getDataTransacao(), b.getDataTransacao());
            if (h < 0 || h > 24) {
                continue;
            }
            if (!keyPrimeira.equals(FinanceTextoUtil.chaveAgrupamento(a.getDescricao()))) {
                continue;
            }
            if (!keySegunda.equals(FinanceTextoUtil.chaveAgrupamento(b.getDescricao()))) {
                continue;
            }
            BigDecimal vb = b.getValor() != null ? b.getValor() : BigDecimal.ZERO;
            sum = sum.add(vb);
            n++;
        }
        if (n == 0) {
            return BigDecimal.ZERO;
        }
        return sum.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
    }

    public List<MemoriaSemanticaTimelineItemDTO> listarRecentesParaUsuario(Long usuarioId, int limite) {
        if (usuarioId == null || limite <= 0) {
            return List.of();
        }
        int cap = Math.min(limite, 120);
        String sql = "SELECT id, contexto, categoria_origem, data_registro, (embedding IS NOT NULL) AS tem_emb, "
            + "tipo, status, confianca, contador_reforco "
            + "FROM memoria_semantica_jarvis WHERE usuario_id = ? AND status = 'ATIVA' "
            + "ORDER BY data_registro DESC LIMIT ?";
        try {
            return jdbcTemplate.query(sql, (rs, rowNum) -> mapTimelineRow(rs), usuarioId, cap);
        } catch (DataAccessException dex) {
            if (isMemoriaSemanticsUnavailable(dex)) {
                logMemoriaUnavailableOnce(dex);
                return List.of();
            }
            throw dex;
        }
    }

    /**
     * Provisão determinística (2.2): PLANO_FUTURO com mês-alvo = mês pedido, por metadado (não similaridade).
     * Devolve linhas (contexto, valor, categoria).
     */
    public List<PlanoFuturoMemoria> listarPlanosFuturosParaMes(Long usuarioId, int mes, int ano) {
        if (usuarioId == null || mes < 1 || mes > 12) {
            return List.of();
        }
        String sql = "SELECT id, contexto, valor, categoria FROM memoria_semantica_jarvis "
            + "WHERE usuario_id = ? AND tipo = 'PLANO_FUTURO' AND status = 'ATIVA' "
            + "AND mes_alvo = ? AND (ano_alvo IS NULL OR ano_alvo = ?) "
            + "ORDER BY data_registro DESC LIMIT 20";
        try {
            return jdbcTemplate.query(sql, (rs, rn) -> new PlanoFuturoMemoria(
                rs.getLong("id"),
                rs.getString("contexto"),
                rs.getBigDecimal("valor"),
                rs.getString("categoria")), usuarioId, mes, ano);
        } catch (DataAccessException dex) {
            if (isMemoriaSemanticsUnavailable(dex)) {
                logMemoriaUnavailableOnce(dex);
                return List.of();
            }
            throw dex;
        }
    }

    public record PlanoFuturoMemoria(Long id, String contexto, BigDecimal valor, String categoria) { }

    /** Refutação (6.1): usuário marca a memória como errada — sai do RAG e do painel. Checa posse. */
    @Transactional
    public boolean refutarMemoria(Long usuarioId, Long memoriaId) {
        if (usuarioId == null || memoriaId == null) {
            return false;
        }
        try {
            return jdbcTemplate.update(
                "UPDATE memoria_semantica_jarvis SET status = 'REFUTADA', confirmada_usuario = FALSE "
                    + "WHERE id = ? AND usuario_id = ? AND status = 'ATIVA'",
                memoriaId, usuarioId) > 0;
        } catch (DataAccessException dex) {
            if (isMemoriaSemanticsUnavailable(dex)) {
                logMemoriaUnavailableOnce(dex);
                return false;
            }
            throw dex;
        }
    }

    /** «Jarvis, esquece isso» — refuta a memória ATIVA mais recente e devolve o contexto refutado. */
    @Transactional
    public Optional<String> refutarMaisRecente(Long usuarioId) {
        if (usuarioId == null) {
            return Optional.empty();
        }
        try {
            List<String> out = jdbcTemplate.query(
                "UPDATE memoria_semantica_jarvis SET status = 'REFUTADA', confirmada_usuario = FALSE "
                    + "WHERE id = (SELECT id FROM memoria_semantica_jarvis WHERE usuario_id = ? AND status = 'ATIVA' "
                    + "ORDER BY GREATEST(data_registro, COALESCE(ultimo_reforco_em, data_registro)) DESC LIMIT 1) "
                    + "AND usuario_id = ? RETURNING contexto",
                (rs, rn) -> rs.getString(1), usuarioId, usuarioId);
            return out.isEmpty() ? Optional.empty() : Optional.ofNullable(out.get(0));
        } catch (DataAccessException dex) {
            if (isMemoriaSemanticsUnavailable(dex)) {
                logMemoriaUnavailableOnce(dex);
                return Optional.empty();
            }
            throw dex;
        }
    }

    /**
     * Invalidação retroativa (1.1): transações-evidência foram excluídas/estornadas →
     * re-valida as memórias que as citam; se o suporte vivo caiu abaixo do mínimo, INVALIDADA na hora.
     */
    @Transactional
    public int invalidarPorEvidencia(Long usuarioId, List<Long> transacoesExcluidas) {
        if (usuarioId == null || transacoesExcluidas == null || transacoesExcluidas.isEmpty()) {
            return 0;
        }
        List<Object[]> candidatas;
        try {
            candidatas = jdbcTemplate.query(
                "SELECT id, transacoes_evidencia FROM memoria_semantica_jarvis "
                    + "WHERE usuario_id = ? AND status = 'ATIVA' AND transacoes_evidencia IS NOT NULL",
                (rs, rn) -> new Object[] {rs.getLong(1), rs.getString(2)}, usuarioId);
        } catch (DataAccessException dex) {
            if (isMemoriaSemanticsUnavailable(dex)) {
                logMemoriaUnavailableOnce(dex);
                return 0;
            }
            throw dex;
        }
        java.util.Set<Long> excluidas = new java.util.HashSet<>(transacoesExcluidas);
        int invalidadas = 0;
        for (Object[] row : candidatas) {
            Long memId = (Long) row[0];
            List<Long> evidencia = parseEvidencia((String) row[1]);
            if (evidencia.isEmpty() || evidencia.stream().noneMatch(excluidas::contains)) {
                continue;
            }
            int vivas = contarEvidenciaViva(evidencia);
            if (vivas < memoriaProps.getHabitoMinOcorrencias()) {
                jdbcTemplate.update(
                    "UPDATE memoria_semantica_jarvis SET status = 'INVALIDADA' WHERE id = ? AND usuario_id = ?",
                    memId, usuarioId);
                invalidadas++;
                log.info("[MEMORIA] Memória id={} invalidada retroativamente (evidência viva={} < mínimo={}) userId={}",
                    memId, vivas, memoriaProps.getHabitoMinOcorrencias(), usuarioId);
            }
        }
        return invalidadas;
    }

    private static List<Long> parseEvidencia(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<Long> out = new ArrayList<>();
        for (String p : csv.split(",")) {
            try {
                out.add(Long.parseLong(p.trim()));
            } catch (NumberFormatException ignore) {
                // token corrompido no CSV — ignora
            }
        }
        return out;
    }

    private int contarEvidenciaViva(List<Long> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        StringBuilder in = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                in.append(',');
            }
            in.append('?');
        }
        Integer n = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM transacoes WHERE id IN (" + in + ") "
                + "AND excluido = FALSE AND status_conferencia = 'CONFIRMADA'",
            Integer.class, ids.toArray());
        return n != null ? n : 0;
    }

    /** Insights para o card do dashboard (3.3): top-K ATIVA por confiança × recência × reforço, sem embedding. */
    public List<MemoriaSemanticaTimelineItemDTO> listarInsightsRelevantes(Long usuarioId, int k) {
        if (usuarioId == null || k <= 0) {
            return List.of();
        }
        double lambdaPorDia = Math.log(2) / Math.max(1, memoriaProps.getRagMeiaVidaDias());
        String sql = "SELECT id, contexto, categoria_origem, data_registro, (embedding IS NOT NULL) AS tem_emb, "
            + "tipo, status, confianca, contador_reforco "
            + "FROM memoria_semantica_jarvis WHERE usuario_id = ? AND status = 'ATIVA' "
            + "ORDER BY confianca "
            + " * exp(-GREATEST(0, EXTRACT(EPOCH FROM (NOW() - COALESCE(ultimo_reforco_em, data_registro)))) / 86400.0 * ?) "
            + " * (1 + LEAST(contador_reforco, 10) * 0.05) DESC "
            + "LIMIT ?";
        try {
            return jdbcTemplate.query(sql, (rs, rn) -> mapTimelineRow(rs), usuarioId, lambdaPorDia, Math.min(k, 10));
        } catch (DataAccessException dex) {
            if (isMemoriaSemanticsUnavailable(dex)) {
                logMemoriaUnavailableOnce(dex);
                return List.of();
            }
            throw dex;
        }
    }

    /** Hábito inferido ainda não confirmado pelo usuário (6.2) — o mais recente, se houver. */
    public Optional<MemoriaSemanticaTimelineItemDTO> buscarHabitoNaoConfirmado(Long usuarioId) {
        if (usuarioId == null) {
            return Optional.empty();
        }
        String sql = "SELECT id, contexto, categoria_origem, data_registro, (embedding IS NOT NULL) AS tem_emb, "
            + "tipo, status, confianca, contador_reforco "
            + "FROM memoria_semantica_jarvis "
            + "WHERE usuario_id = ? AND status = 'ATIVA' AND tipo = 'HABITO' AND origem = 'INFERIDO' "
            + "AND confirmada_usuario IS NULL ORDER BY data_registro DESC LIMIT 1";
        try {
            List<MemoriaSemanticaTimelineItemDTO> out =
                jdbcTemplate.query(sql, (rs, rn) -> mapTimelineRow(rs), usuarioId);
            return out.isEmpty() ? Optional.empty() : Optional.of(out.get(0));
        } catch (DataAccessException dex) {
            if (isMemoriaSemanticsUnavailable(dex)) {
                logMemoriaUnavailableOnce(dex);
                return Optional.empty();
            }
            throw dex;
        }
    }

    /** Resposta do usuário à pergunta de padrão (6.2): sim → confiança alta; não → REFUTADA. */
    @Transactional
    public void confirmarHabito(Long usuarioId, Long memoriaId, boolean confirmado) {
        if (usuarioId == null || memoriaId == null) {
            return;
        }
        try {
            if (confirmado) {
                jdbcTemplate.update(
                    "UPDATE memoria_semantica_jarvis SET confianca = 0.90, confirmada_usuario = TRUE "
                        + "WHERE id = ? AND usuario_id = ?",
                    memoriaId, usuarioId);
            } else {
                jdbcTemplate.update(
                    "UPDATE memoria_semantica_jarvis SET status = 'REFUTADA', confirmada_usuario = FALSE "
                        + "WHERE id = ? AND usuario_id = ?",
                    memoriaId, usuarioId);
            }
        } catch (DataAccessException dex) {
            if (isMemoriaSemanticsUnavailable(dex)) {
                logMemoriaUnavailableOnce(dex);
                return;
            }
            throw dex;
        }
    }

    /** SUPERADA recentes para auditoria no painel (item 4): rastreáveis via superada_por_id. */
    public List<MemoriaSemanticaTimelineItemDTO> listarSuperadasRecentes(Long usuarioId, int limite) {
        if (usuarioId == null || limite <= 0) {
            return List.of();
        }
        String sql = "SELECT id, contexto, categoria_origem, data_registro, (embedding IS NOT NULL) AS tem_emb, "
            + "tipo, status, confianca, contador_reforco "
            + "FROM memoria_semantica_jarvis WHERE usuario_id = ? AND status = 'SUPERADA' "
            + "ORDER BY data_registro DESC LIMIT ?";
        try {
            return jdbcTemplate.query(sql, (rs, rn) -> mapTimelineRow(rs), usuarioId, Math.min(limite, 30));
        } catch (DataAccessException dex) {
            if (isMemoriaSemanticsUnavailable(dex)) {
                logMemoriaUnavailableOnce(dex);
                return List.of();
            }
            throw dex;
        }
    }

    /**
     * Reverte uma superação errada: volta a ATIVA e marca {@code restaurada_em} — a partir daí a
     * memória fica fora do mecanismo de contradição (não re-supera em loop).
     */
    @Transactional
    public boolean restaurarMemoria(Long usuarioId, Long memoriaId) {
        if (usuarioId == null || memoriaId == null) {
            return false;
        }
        try {
            return jdbcTemplate.update(
                "UPDATE memoria_semantica_jarvis SET status = 'ATIVA', superada_por_id = NULL, restaurada_em = NOW() "
                    + "WHERE id = ? AND usuario_id = ? AND status = 'SUPERADA'",
                memoriaId, usuarioId) > 0;
        } catch (DataAccessException dex) {
            if (isMemoriaSemanticsUnavailable(dex)) {
                logMemoriaUnavailableOnce(dex);
                return false;
            }
            throw dex;
        }
    }

    private static MemoriaSemanticaTimelineItemDTO mapTimelineRow(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("data_registro");
        Instant inst = ts != null ? ts.toInstant() : Instant.EPOCH;
        return MemoriaSemanticaTimelineItemDTO.builder()
            .id(rs.getLong("id"))
            .contexto(rs.getString("contexto"))
            .categoriaOrigem(rs.getString("categoria_origem"))
            .dataRegistro(inst)
            .temEmbedding(rs.getBoolean("tem_emb"))
            .tipo(rs.getString("tipo"))
            .status(rs.getString("status"))
            .confianca(rs.getBigDecimal("confianca"))
            .contadorReforco(rs.getInt("contador_reforco"))
            .build();
    }

    private static MemoriaSemanticaSimilaridadeDTO mapSimilarRow(ResultSet rs) throws SQLException {
        double dist = rs.getDouble("dist");
        int pct = (int) Math.round(Math.max(0, Math.min(1, 1.0 - dist)) * 100.0);
        Timestamp ts = rs.getTimestamp("data_registro");
        LocalDateTime dt = ts != null
            ? ts.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
            : LocalDateTime.now();
        return MemoriaSemanticaSimilaridadeDTO.builder()
            .contexto(rs.getString("contexto"))
            .dataRegistro(dt)
            .distanciaCosseno(dist)
            .similaridadePercentual(pct)
            .build();
    }

    private static PGobject pgVector(float[] values) {
        try {
            PGobject o = new PGobject();
            o.setType("vector");
            o.setValue(toVectorLiteral(values));
            return o;
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao construir literal pgvector", e);
        }
    }

    private static String toVectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
