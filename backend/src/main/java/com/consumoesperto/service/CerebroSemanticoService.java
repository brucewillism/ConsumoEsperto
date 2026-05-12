package com.consumoesperto.service;

import com.consumoesperto.dto.GatilhoHabitoDeteccaoDTO;
import com.consumoesperto.dto.MemoriaSemanticaSimilaridadeDTO;
import com.consumoesperto.dto.MemoriaSemanticaTimelineItemDTO;
import com.consumoesperto.model.MemoriaCategoriaOrigem;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.util.FinanceTextoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PGobject;
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
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
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

    @Transactional
    public void gravarMemoria(Long usuarioId, String contexto, MemoriaCategoriaOrigem categoria) {
        if (usuarioId == null || contexto == null || contexto.isBlank() || categoria == null) {
            return;
        }
        String ctx = contexto.trim();
        PGobject vec = null;
        Optional<float[]> emb = openAiService.tryCreateEmbedding(ctx, usuarioId);
        if (emb.isPresent()) {
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
        jdbcTemplate.update(
            "INSERT INTO memoria_semantica_jarvis (usuario_id, contexto, embedding, categoria_origem) VALUES (?,?,?,?)",
            usuarioId,
            ctx,
            vec,
            categoria.name());
    }

    public List<MemoriaSemanticaSimilaridadeDTO> buscarTop3Similares(Long usuarioId, String textoConsulta) {
        if (usuarioId == null || textoConsulta == null || textoConsulta.isBlank()) {
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
        PGobject probe = pgVector(q);
        String sql = "SELECT contexto, data_registro, (embedding <=> (?::vector)) AS dist "
            + "FROM memoria_semantica_jarvis "
            + "WHERE usuario_id = ? AND embedding IS NOT NULL "
            + "ORDER BY embedding <=> (?::vector) "
            + "LIMIT " + TOP_K;
        return jdbcTemplate.query(
            connection -> {
                var ps = connection.prepareStatement(sql);
                ps.setObject(1, probe);
                ps.setLong(2, usuarioId);
                ps.setObject(3, probe);
                return ps;
            },
            (rs, rowNum) -> mapSimilarRow(rs));
    }

    public List<String> listarContextosMemoriaNoMesCalendario(Long usuarioId, int mes, int ano) {
        if (usuarioId == null || mes < 1 || mes > 12) {
            return List.of();
        }
        String sql = "SELECT contexto FROM memoria_semantica_jarvis WHERE usuario_id = ? "
            + "AND EXTRACT(MONTH FROM data_registro) = ? AND EXTRACT(YEAR FROM data_registro) = ? "
            + "ORDER BY data_registro DESC LIMIT 80";
        return jdbcTemplate.query(sql, (rs, rn) -> rs.getString(1), usuarioId, mes, ano);
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
            + "ORDER BY data_registro DESC LIMIT 80";
        List<String> ctxs = jdbcTemplate.query(sql, (rs, i) -> rs.getString(1), usuarioId);
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
        String sql = "SELECT id, contexto, categoria_origem, data_registro, (embedding IS NOT NULL) AS tem_emb "
            + "FROM memoria_semantica_jarvis WHERE usuario_id = ? ORDER BY data_registro DESC LIMIT ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Timestamp ts = rs.getTimestamp("data_registro");
            Instant inst = ts != null ? ts.toInstant() : Instant.EPOCH;
            return MemoriaSemanticaTimelineItemDTO.builder()
                .id(rs.getLong("id"))
                .contexto(rs.getString("contexto"))
                .categoriaOrigem(rs.getString("categoria_origem"))
                .dataRegistro(inst)
                .temEmbedding(rs.getBoolean("tem_emb"))
                .build();
        }, usuarioId, cap);
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
