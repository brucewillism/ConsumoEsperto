package com.consumoesperto.service;

import com.consumoesperto.dto.JarvisNotificacaoPreferenciasDTO;
import com.consumoesperto.model.JarvisTipoNotificacaoProativa;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.model.UsuarioAiConfig;
import com.consumoesperto.repository.UsuarioAiConfigRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class JarvisNotificacaoPreferenciasService {

    private final UsuarioAiConfigRepository usuarioAiConfigRepository;
    private final UsuarioRepository usuarioRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public JarvisNotificacaoPreferenciasDTO obter(Long usuarioId) {
        return lerOuDefaults(usuarioId);
    }

    @Transactional
    public JarvisNotificacaoPreferenciasDTO salvar(Long usuarioId, JarvisNotificacaoPreferenciasDTO patch) {
        JarvisNotificacaoPreferenciasDTO atual = lerOuDefaults(usuarioId);
        if (patch.getAlertaRiscoReativo() != null) {
            atual.setAlertaRiscoReativo(patch.getAlertaRiscoReativo());
        }
        if (patch.getResumoSemanal() != null) {
            atual.setResumoSemanal(patch.getResumoSemanal());
        }
        if (patch.getRelatorioMensalScore() != null) {
            atual.setRelatorioMensalScore(patch.getRelatorioMensalScore());
        }
        if (patch.getDigestMensalSentinela() != null) {
            atual.setDigestMensalSentinela(patch.getDigestMensalSentinela());
        }
        if (patch.getSentinelaDia5() != null) {
            atual.setSentinelaDia5(patch.getSentinelaDia5());
        }
        if (patch.getRecorrenciasVencimento() != null) {
            atual.setRecorrenciasVencimento(patch.getRecorrenciasVencimento());
        }
        if (patch.getAmortizacaoSazonal() != null) {
            atual.setAmortizacaoSazonal(patch.getAmortizacaoSazonal());
        }
        if (patch.getConferenciaNotas() != null) {
            atual.setConferenciaNotas(patch.getConferenciaNotas());
        }
        if (patch.getModoViagemCronos() != null) {
            atual.setModoViagemCronos(patch.getModoViagemCronos());
        }
        persistir(usuarioId, atual);
        return atual;
    }

    @Transactional(readOnly = true)
    public boolean estaAtiva(Long usuarioId, JarvisTipoNotificacaoProativa tipo) {
        if (usuarioId == null || tipo == null) {
            return true;
        }
        JarvisNotificacaoPreferenciasDTO prefs = lerOuDefaults(usuarioId);
        return switch (tipo) {
            case ALERTA_RISCO_REATIVO -> bool(prefs.getAlertaRiscoReativo());
            case RESUMO_SEMANAL -> bool(prefs.getResumoSemanal());
            case RELATORIO_MENSAL_SCORE -> bool(prefs.getRelatorioMensalScore());
            case DIGEST_MENSAL_SENTINELA -> bool(prefs.getDigestMensalSentinela());
            case SENTINELA_DIA5 -> bool(prefs.getSentinelaDia5());
            case RECORRENCIAS_VENCIMENTO -> bool(prefs.getRecorrenciasVencimento());
            case AMORTIZACAO_SAZONAL -> bool(prefs.getAmortizacaoSazonal());
            case CONFERENCIA_NOTAS -> bool(prefs.getConferenciaNotas());
            case MODO_VIAGEM_CRONOS -> bool(prefs.getModoViagemCronos());
        };
    }

    private JarvisNotificacaoPreferenciasDTO lerOuDefaults(Long usuarioId) {
        return usuarioAiConfigRepository.findByUsuarioId(usuarioId)
            .map(cfg -> fromJson(cfg.getJarvisNotifPrefsJson()))
            .orElseGet(JarvisNotificacaoPreferenciasDTO::defaults);
    }

    private void persistir(Long usuarioId, JarvisNotificacaoPreferenciasDTO prefs) {
        UsuarioAiConfig cfg = usuarioAiConfigRepository.findByUsuarioId(usuarioId)
            .orElseGet(() -> newEntity(usuarioId));
        try {
            cfg.setJarvisNotifPrefsJson(objectMapper.writeValueAsString(prefs));
        } catch (Exception e) {
            log.warn("Serializar prefs notificação userId={}: {}", usuarioId, e.getMessage());
            return;
        }
        usuarioAiConfigRepository.save(cfg);
    }

    private UsuarioAiConfig newEntity(Long usuarioId) {
        Usuario u = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));
        UsuarioAiConfig c = new UsuarioAiConfig();
        c.setUsuario(u);
        return c;
    }

    private JarvisNotificacaoPreferenciasDTO fromJson(String json) {
        if (json == null || json.isBlank()) {
            return JarvisNotificacaoPreferenciasDTO.defaults();
        }
        try {
            JarvisNotificacaoPreferenciasDTO parsed =
                objectMapper.readValue(json, JarvisNotificacaoPreferenciasDTO.class);
            return mergeDefaults(parsed);
        } catch (Exception e) {
            log.debug("Prefs notificação JSON inválido, usando defaults: {}", e.getMessage());
            return JarvisNotificacaoPreferenciasDTO.defaults();
        }
    }

    private static JarvisNotificacaoPreferenciasDTO mergeDefaults(JarvisNotificacaoPreferenciasDTO parsed) {
        JarvisNotificacaoPreferenciasDTO d = JarvisNotificacaoPreferenciasDTO.defaults();
        if (parsed.getAlertaRiscoReativo() != null) {
            d.setAlertaRiscoReativo(parsed.getAlertaRiscoReativo());
        }
        if (parsed.getResumoSemanal() != null) {
            d.setResumoSemanal(parsed.getResumoSemanal());
        }
        if (parsed.getRelatorioMensalScore() != null) {
            d.setRelatorioMensalScore(parsed.getRelatorioMensalScore());
        }
        if (parsed.getDigestMensalSentinela() != null) {
            d.setDigestMensalSentinela(parsed.getDigestMensalSentinela());
        }
        if (parsed.getSentinelaDia5() != null) {
            d.setSentinelaDia5(parsed.getSentinelaDia5());
        }
        if (parsed.getRecorrenciasVencimento() != null) {
            d.setRecorrenciasVencimento(parsed.getRecorrenciasVencimento());
        }
        if (parsed.getAmortizacaoSazonal() != null) {
            d.setAmortizacaoSazonal(parsed.getAmortizacaoSazonal());
        }
        if (parsed.getConferenciaNotas() != null) {
            d.setConferenciaNotas(parsed.getConferenciaNotas());
        }
        if (parsed.getModoViagemCronos() != null) {
            d.setModoViagemCronos(parsed.getModoViagemCronos());
        }
        return d;
    }

    private static boolean bool(Boolean v) {
        return v == null || v;
    }
}
