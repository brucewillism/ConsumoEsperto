package com.consumoesperto.service;

import com.consumoesperto.model.AlertaEnviado;
import com.consumoesperto.model.GravidadeAlertaFluxo;
import com.consumoesperto.model.RiscoFluxo;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.AlertaEnviadoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Sentinela v8 — despacho proativo via Evolution (texto livre), opt-in e idempotência.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertaDispatchService {

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    private final AlertaEnviadoRepository alertaEnviadoRepository;
    private final WhatsAppNotificationService whatsAppNotificationService;
    private final JarvisProtocolService jarvisProtocolService;
    private final UsuarioRepository usuarioRepository;

    @Transactional
    public boolean dispatchAlerta(Usuario usuario, RiscoFluxo risco) {
        if (usuario == null || risco == null || !risco.temRisco()) {
            return false;
        }
        if (!isOptIn(usuario)) {
            log.debug("[SENTINELA] Usuário {} sem opt-in — alerta omitido.", usuario.getId());
            return false;
        }
        if (usuario.getWhatsappNumero() == null || usuario.getWhatsappNumero().isBlank()) {
            return false;
        }

        AlertaEnviado anterior = alertaEnviadoRepository
            .findTopByUsuarioIdAndPeriodoOrderByDataEnvioDesc(usuario.getId(), risco.getPeriodo())
            .orElse(null);
        boolean piorou = anterior == null
            || risco.getGravidade().ordinal() > anterior.getGravidade().ordinal();
        if (!piorou) {
            log.debug("[SENTINELA] Alerta omitido (idempotência) userId={} periodo={} gravidade={}",
                usuario.getId(), risco.getPeriodo(), risco.getGravidade());
            return false;
        }

        String mensagem = montarMensagemRica(usuario.getId(), risco);
        boolean enviado = whatsAppNotificationService.enviarParaUsuario(usuario.getId(), mensagem);
        if (enviado) {
            AlertaEnviado registro = new AlertaEnviado();
            registro.setUsuarioId(usuario.getId());
            registro.setPeriodo(risco.getPeriodo());
            registro.setGravidade(risco.getGravidade());
            registro.setDataEnvio(LocalDate.now());
            alertaEnviadoRepository.save(registro);
            log.info("[SENTINELA] Alerta {} enviado userId={} periodo={}",
                risco.getGravidade(), usuario.getId(), risco.getPeriodo());
        }
        return enviado;
    }

    private boolean isOptIn(Usuario usuario) {
        Boolean opt = usuario.getOptInNotificacoes();
        return opt == null || Boolean.TRUE.equals(opt);
    }

    private String montarMensagemRica(Long usuarioId, RiscoFluxo risco) {
        String voc = jarvisProtocolService.resolveVocative(usuarioId, usuarioRepository);
        if (risco.getGravidade() == GravidadeAlertaFluxo.VERMELHO) {
            return "⚠️ *Alerta de fluxo — crítico*\n\n"
                + voc + ", na semana de *" + risco.getSemanaFormatada() + "* "
                + "a projeção indica saldo *negativo* de "
                + BRL.format(risco.getValorReferencia().abs()) + ".\n\n"
                + "Quer que eu detalhe as saídas previstas (parcelas, fixas e provisões)? "
                + "Responda *como está meu fluxo*.";
        }
        return "ℹ️ *Alerta de fluxo — atenção*\n\n"
            + voc + ", seu *escudo de reserva* pode cair para *"
            + risco.getValorReferencia().setScale(1, java.math.RoundingMode.HALF_UP)
            + " meses* até *" + risco.getSemanaFormatada() + "*.\n\n"
            + "Recomendo rever compromissos fixos e parcelas. "
            + "Responda *disponibilidade real* para ver o panorama completo.";
    }
}
