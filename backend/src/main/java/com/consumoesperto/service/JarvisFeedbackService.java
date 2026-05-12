package com.consumoesperto.service;

import com.consumoesperto.dto.JarvisFeedbackRequest;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Aprendizado por reforço — registo de feedback sobre insights/protocolos.
 */
@Service
@RequiredArgsConstructor
public class JarvisFeedbackService {

    private final JdbcTemplate jdbcTemplate;
    private final WhatsAppNotificationService whatsAppNotificationService;
    private final JarvisProtocolService jarvisProtocolService;
    private final UsuarioRepository usuarioRepository;

    @Transactional
    public void registrar(Long usuarioId, JarvisFeedbackRequest req) {
        if (usuarioId == null || req == null) {
            return;
        }
        Timestamp expiracao = null;
        if (Boolean.FALSE.equals(req.getPositivo())) {
            expiracao = Timestamp.valueOf(LocalDateTime.now().plusDays(30));
        }
        jdbcTemplate.update(
            "INSERT INTO jarvis_feedback (usuario_id, insight_id, tipo_alvo, positivo, categoria_chave, data_expiracao) "
                + "VALUES (?,?,?,?,?,?)",
            usuarioId,
            req.getInsightId(),
            req.getTipoAlvo(),
            req.getPositivo(),
            req.getCategoriaChave(),
            expiracao);

        if (Boolean.FALSE.equals(req.getPositivo())) {
            String voc = jarvisProtocolService.resolveVocative(usuarioId, usuarioRepository);
            whatsAppNotificationService.enviarParaUsuario(
                usuarioId,
                jarvisProtocolService.msgRecalibracaoPosFeedbackNegativo(voc));
        }
    }

    /** Feedback negativo ainda ativo (expiração futura ou legado 30 dias desde o registo). */
    public boolean feedbackNegativoAtivoParaCategoria(Long usuarioId, String categoriaChave) {
        if (usuarioId == null || categoriaChave == null || categoriaChave.isBlank()) {
            return false;
        }
        Integer n = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM jarvis_feedback WHERE usuario_id = ? AND positivo = FALSE AND "
                + "TRIM(LOWER(COALESCE(categoria_chave, ''))) = TRIM(LOWER(?)) AND ( "
                + "(data_expiracao IS NOT NULL AND data_expiracao > CURRENT_TIMESTAMP) OR "
                + "(data_expiracao IS NULL AND data_registro > CURRENT_TIMESTAMP - INTERVAL '30 days'))",
            Integer.class,
            usuarioId,
            categoriaChave.trim());
        return n != null && n > 0;
    }

    /** Multiplicador para impacto/peso de sugestões quando o utilizador rejeitou a mesma chave (período ativo). */
    public BigDecimal fatorPesoSugestao30d(Long usuarioId, String categoriaOuRotuloChave) {
        if (usuarioId == null || categoriaOuRotuloChave == null || categoriaOuRotuloChave.isBlank()) {
            return BigDecimal.ONE;
        }
        Integer n = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM jarvis_feedback WHERE usuario_id = ? AND positivo = FALSE AND "
                + "TRIM(LOWER(COALESCE(categoria_chave, ''))) = TRIM(LOWER(?)) AND ( "
                + "(data_expiracao IS NOT NULL AND data_expiracao > CURRENT_TIMESTAMP) OR "
                + "(data_expiracao IS NULL AND data_registro > CURRENT_TIMESTAMP - INTERVAL '30 days'))",
            Integer.class,
            usuarioId,
            categoriaOuRotuloChave.trim());
        if (n != null && n > 0) {
            return new BigDecimal("0.55");
        }
        return BigDecimal.ONE;
    }

    public boolean isMacroEmPenalidade30d(Long usuarioId, String categoriaMacro) {
        if (usuarioId == null || categoriaMacro == null || categoriaMacro.isBlank()) {
            return false;
        }
        Integer n = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM jarvis_feedback WHERE usuario_id = ? AND tipo_alvo = 'CONTENCAO' "
                + "AND positivo = FALSE AND COALESCE(TRIM(categoria_chave), '') = TRIM(?) AND ( "
                + "(data_expiracao IS NOT NULL AND data_expiracao > CURRENT_TIMESTAMP) OR "
                + "(data_expiracao IS NULL AND data_registro > CURRENT_TIMESTAMP - INTERVAL '30 days'))",
            Integer.class,
            usuarioId,
            categoriaMacro.trim());
        return n != null && n > 0;
    }
}
