package com.consumoesperto.service;

import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Protocolo Memória Semântica (RAG): embeddings por transação em {@code transacao_semantica_index} (pgvector).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransacaoSemanticaIndexService {

    private static final int DIM_VETOR = 1536;
    private static final int DEFAULT_TOP_K = 15;

    private final JdbcTemplate jdbcTemplate;
    private final TransacaoRepository transacaoRepository;
    private final OpenAiService openAiService;

    @Async
    public void agendarIndexacao(Long transacaoId) {
        if (transacaoId == null) {
            return;
        }
        try {
            indexarTransacao(transacaoId);
        } catch (Exception e) {
            log.warn("[JARVIS-LOG] Indexação semântica falhou transacaoId={}: {}", transacaoId, e.getMessage());
        }
    }

    @Transactional
    public void indexarTransacao(Long transacaoId) {
        Optional<Transacao> opt = transacaoRepository.findById(transacaoId);
        if (opt.isEmpty()) {
            return;
        }
        Transacao t = opt.get();
        if (t.isExcluido() || t.getUsuario() == null) {
            jdbcTemplate.update("DELETE FROM transacao_semantica_index WHERE transacao_id = ?", transacaoId);
            log.info("[JARVIS-LOG] Removido índice semântico transacaoId={} (excluída ou sem usuário).", transacaoId);
            return;
        }
        String texto = textoParaEmbedding(t);
        Long usuarioId = t.getUsuario().getId();
        Optional<float[]> embOpt = openAiService.tryCreateEmbedding(texto, usuarioId);
        PGobject vec = null;
        if (embOpt.isPresent()) {
            float[] f = embOpt.get();
            if (f.length == DIM_VETOR) {
                vec = pgVector(f);
            } else {
                log.warn("[JARVIS-LOG] Embedding dimensão {} (esperado {}); gravação sem vetor.", f.length, DIM_VETOR);
            }
        }
        jdbcTemplate.update(
            "INSERT INTO transacao_semantica_index (transacao_id, usuario_id, texto_indexado, embedding, atualizado_em) "
                + "VALUES (?,?,?,?, NOW()) ON CONFLICT (transacao_id) DO UPDATE SET "
                + "usuario_id = EXCLUDED.usuario_id, texto_indexado = EXCLUDED.texto_indexado, "
                + "embedding = EXCLUDED.embedding, atualizado_em = NOW()",
            t.getId(),
            usuarioId,
            texto,
            vec
        );
        log.info("[JARVIS-LOG] Índice semântico atualizado transacaoId={} usuarioId={}", transacaoId, usuarioId);
    }

    /**
     * Recupera trechos de transações semanticamente próximos da pergunta (para RAG).
     */
    public String montarContextoParaRag(Long usuarioId, String pergunta, int topK) {
        if (usuarioId == null || pergunta == null || pergunta.isBlank()) {
            return "";
        }
        Optional<float[]> query = openAiService.tryCreateEmbedding(pergunta.trim(), usuarioId);
        if (query.isEmpty()) {
            return "";
        }
        float[] q = query.get();
        if (q.length != DIM_VETOR) {
            return "";
        }
        PGobject probe = pgVector(q);
        int k = topK <= 0 ? DEFAULT_TOP_K : Math.min(topK, 40);
        String sql = "SELECT texto_indexado, (embedding <=> (?::vector)) AS dist "
            + "FROM transacao_semantica_index "
            + "WHERE usuario_id = ? AND embedding IS NOT NULL "
            + "ORDER BY embedding <=> (?::vector) "
            + "LIMIT " + k;
        List<String> linhas = jdbcTemplate.query(
            con -> {
                PreparedStatement ps = con.prepareStatement(sql);
                ps.setObject(1, probe);
                ps.setLong(2, usuarioId);
                ps.setObject(3, probe);
                return ps;
            },
            (rs, rowNum) -> rs.getString("texto_indexado")
        );
        if (linhas.isEmpty()) {
            log.info("[JARVIS-LOG] RAG transações: nenhum vetor indexado para usuarioId={}", usuarioId);
            return "";
        }
        return String.join("\n", linhas);
    }

    static String textoParaEmbedding(Transacao t) {
        String cat = t.getCategoria() != null && t.getCategoria().getNome() != null ? t.getCategoria().getNome() : "?";
        String tipo = t.getTipoTransacao() != null ? t.getTipoTransacao().name() : "?";
        String data = t.getDataTransacao() != null ? t.getDataTransacao().toLocalDate().toString() : "";
        String parcela = "";
        if (t.getParcelaAtual() != null && t.getTotalParcelas() != null && t.getTotalParcelas() > 0) {
            parcela = " parcela " + t.getParcelaAtual() + "/" + t.getTotalParcelas();
        }
        return data + " | " + tipo + " | " + t.getDescricao() + parcela + " | R$"
            + t.getValor().setScale(2, RoundingMode.HALF_UP) + " | " + cat;
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
