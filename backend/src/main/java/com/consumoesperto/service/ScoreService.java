package com.consumoesperto.service;

import com.consumoesperto.dto.UsuarioScoreDTO;
import com.consumoesperto.model.HistoricoScore;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.model.UsuarioScore;
import com.consumoesperto.repository.HistoricoScoreRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.repository.UsuarioScoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ScoreService {

    private static final int SCORE_INICIAL = 500;
    private static final NumberFormat BRL_ECON = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    private final UsuarioScoreRepository usuarioScoreRepository;
    private final HistoricoScoreRepository historicoScoreRepository;
    private final UsuarioRepository usuarioRepository;
    private final JarvisProtocolService jarvisProtocolService;

    @Transactional(readOnly = true)
    public UsuarioScoreDTO obter(Long usuarioId) {
        return toDto(usuarioScoreRepository.findByUsuarioId(usuarioId).orElseGet(() -> novoTransient(usuarioId)));
    }

    @Transactional
    public UsuarioScoreDTO registrarEvento(Long usuarioId, EventoScore evento, String detalhe) {
        UsuarioScore score = usuarioScoreRepository.findByUsuarioId(usuarioId).orElseGet(() -> criar(usuarioId));
        int novoScore = Math.max(0, Math.min(1000, score.getScore() + evento.delta));
        score.setScore(novoScore);
        score.setNivel(nivel(novoScore));
        usuarioScoreRepository.save(score);

        HistoricoScore hist = new HistoricoScore();
        hist.setUsuario(score.getUsuario());
        hist.setDelta(evento.delta);
        hist.setScoreResultante(novoScore);
        hist.setMotivo(evento.name());
        hist.setDetalhe(detalhe);
        historicoScoreRepository.save(hist);
        return toDto(score);
    }

    public int estimarPerdaSimulacao(java.math.BigDecimal impactoMensal) {
        if (impactoMensal == null) {
            return 0;
        }
        int perda = impactoMensal.divide(java.math.BigDecimal.valueOf(100), 0, java.math.RoundingMode.CEILING).intValue() * 10;
        return Math.min(150, Math.max(0, perda));
    }

    @Transactional(readOnly = true)
    public String relatorioMensalEconomia(Long usuarioId) {
        YearMonth anterior = YearMonth.now().minusMonths(1);
        LocalDateTime ini = anterior.atDay(1).atStartOfDay();
        LocalDateTime fim = anterior.atEndOfMonth().atTime(23, 59, 59);
        int pontosGanhos = historicoScoreRepository.findByUsuarioIdAndDataEventoBetweenOrderByDataEventoAsc(usuarioId, ini, fim)
            .stream()
            .filter(h -> h.getDelta() != null && h.getDelta() > 0)
            .mapToInt(HistoricoScore::getDelta)
            .sum();
        BigDecimal economiaEstimativa = BigDecimal.valueOf(pontosGanhos).multiply(BigDecimal.valueOf(2)).setScale(2, RoundingMode.HALF_UP);
        UsuarioScoreDTO score = obter(usuarioId);
        String v = jarvisProtocolService.resolveVocative(usuarioId, usuarioRepository);
        return jarvisProtocolService.proativoRelatorioMensalEconomia(v, BRL_ECON.format(economiaEstimativa), score.getScore(), score.getNivel());
    }

    @Transactional(readOnly = true)
    public java.util.List<java.util.Map<String, Object>> historico(Long usuarioId) {
        return historicoScoreRepository.findTop10ByUsuarioIdOrderByDataEventoDesc(usuarioId)
            .stream()
            .map(h -> {
                java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("id", h.getId());
                row.put("delta", h.getDelta());
                row.put("scoreResultante", h.getScoreResultante());
                row.put("motivo", h.getMotivo());
                row.put("detalhe", h.getDetalhe());
                row.put("dataEvento", h.getDataEvento());
                return row;
            })
            .toList();
    }

    private UsuarioScore criar(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));
        UsuarioScore s = new UsuarioScore();
        s.setUsuario(usuario);
        s.setScore(SCORE_INICIAL);
        s.setNivel(nivel(SCORE_INICIAL));
        return usuarioScoreRepository.save(s);
    }

    private UsuarioScore novoTransient(Long usuarioId) {
        UsuarioScore s = new UsuarioScore();
        s.setScore(SCORE_INICIAL);
        s.setNivel(nivel(SCORE_INICIAL));
        return s;
    }

    private UsuarioScoreDTO toDto(UsuarioScore s) {
        UsuarioScoreDTO dto = new UsuarioScoreDTO();
        dto.setScore(s.getScore());
        dto.setNivel(s.getNivel());
        dto.setProximoNivelEm(proximoNivelEm(s.getScore()));
        dto.setDataAtualizacao(s.getDataAtualizacao());
        return dto;
    }

    private static String nivel(int score) {
        if (score >= 850) return "Diamante";
        if (score >= 700) return "Ouro";
        if (score >= 550) return "Prata";
        return "Bronze";
    }

    private static int proximoNivelEm(int score) {
        if (score < 550) return 550 - score;
        if (score < 700) return 700 - score;
        if (score < 850) return 850 - score;
        return 0;
    }

    public enum EventoScore {
        CONSELHO_IA_SEGUIDO(50),
        META_BATIDA(100),
        ORCAMENTO_NO_VERDE(80),
        IMPORTACAO_CONSISTENTE(30),
        ORCAMENTO_ESTOURADO(-40),
        FATURA_VENCIDA(-100),
        ALERTA_PROATIVO_IGNORADO(-20),
        INVESTIMENTO_REGISTRADO(40);

        private final int delta;

        EventoScore(int delta) {
            this.delta = delta;
        }
    }
}
