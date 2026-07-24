package com.consumoesperto.service;

import com.consumoesperto.dto.NotificacaoSolicitacao;
import com.consumoesperto.model.NotificacaoCanalEntrega;
import com.consumoesperto.model.NotificacaoCategoria;
import com.consumoesperto.model.NotificacaoDigestBuffer;
import com.consumoesperto.model.NotificacaoEnviada;
import com.consumoesperto.model.NotificacaoEventoTipo;
import com.consumoesperto.repository.NotificacaoDigestBufferRepository;
import com.consumoesperto.repository.NotificacaoEnviadaRepository;
import com.consumoesperto.util.AppTimeZone;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Centro de orquestração — toda notificação proativa passa por aqui.
 * Jobs e serviços NÃO devem chamar WhatsApp directamente.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationOrchestratorService {

    private static final Duration COOLDOWN_IMPORTANTE = Duration.ofHours(6);
    private static final Duration COOLDOWN_INFORMATIVA = Duration.ofHours(24);
    private static final String DIGEST_TITULO = "Resumo ConsumoEsperto";

    private final NotificacaoEnviadaRepository enviadaRepository;
    private final NotificacaoDigestBufferRepository digestBufferRepository;
    private final JarvisNotificacaoPreferenciasService preferenciasService;
    private final WhatsAppDeliveryService whatsAppDeliveryService;
    private final WebNotificationDeliveryService webNotificationDeliveryService;

    /**
     * Solicita envio de notificação. INFORMATIVA é bufferizada para digest diário quando aplicável.
     */
    @Transactional
    public boolean solicitar(NotificacaoSolicitacao req) {
        if (req == null || req.getUsuarioId() == null || req.getEvento() == null) {
            return false;
        }
        String mensagem = req.getMensagem();
        if (mensagem == null || mensagem.isBlank()) {
            return false;
        }

        NotificacaoEventoTipo evento = req.getEvento();
        NotificacaoCategoria categoria = evento.categoria();

        if (!preferenciaAtiva(req.getUsuarioId(), evento)) {
            log.debug("[Orquestrador] Suprimida por preferência userId={} evento={}",
                req.getUsuarioId(), evento);
            return false;
        }

        String hash = resolverHash(req);

        if (enviadaRepository.existsByUsuarioIdAndHashEvento(req.getUsuarioId(), hash)) {
            log.debug("[Orquestrador] Duplicada userId={} hash={}", req.getUsuarioId(), hash);
            return false;
        }

        if (digestBufferRepository.existsByUsuarioIdAndHashEvento(req.getUsuarioId(), hash)) {
            log.debug("[Orquestrador] Já no buffer userId={} hash={}", req.getUsuarioId(), hash);
            return false;
        }

        if (categoria == NotificacaoCategoria.INFORMATIVA) {
            return enfileirarInformativa(req, hash);
        }

        if (cooldownAtivo(req.getUsuarioId(), categoria)) {
            log.info("[Orquestrador] Cooldown {} userId={} evento={}",
                categoria, req.getUsuarioId(), evento);
            return false;
        }

        return entregar(req.getUsuarioId(), evento, categoria, hash, mensagem, req.getTituloWeb());
    }

    /** Flush diário de digests informativos — após jobs do dia 1 e resumo semanal. */
    @Scheduled(cron = "0 45 19 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public void flushDigestsInformativosAgendado() {
        LocalDate hoje = AppTimeZone.hoje();
        List<Long> usuarios = digestBufferRepository.findDistinctUsuarioIdsByDataRef(hoje);
        for (Long usuarioId : usuarios) {
            try {
                flushDigestUsuario(usuarioId, hoje);
            } catch (Exception e) {
                log.warn("[Orquestrador] Flush digest userId={}: {}", usuarioId, e.getMessage());
            }
        }
    }

    @Transactional
    public boolean flushDigestUsuario(Long usuarioId, LocalDate dataRef) {
        List<NotificacaoDigestBuffer> itens =
            digestBufferRepository.findByUsuarioIdAndDataRefOrderByCriadoEmAsc(usuarioId, dataRef);
        if (itens.isEmpty()) {
            return false;
        }

        if (cooldownAtivo(usuarioId, NotificacaoCategoria.INFORMATIVA)) {
            log.info("[Orquestrador] Digest adiado (cooldown 24h) userId={}", usuarioId);
            return false;
        }

        String mensagem = montarDigest(itens);
        String hash = "DIGEST:" + usuarioId + ":" + dataRef;

        if (enviadaRepository.existsByUsuarioIdAndHashEvento(usuarioId, hash)) {
            digestBufferRepository.deleteByUsuarioIdAndDataRef(usuarioId, dataRef);
            return false;
        }

        boolean ok = entregar(usuarioId, NotificacaoEventoTipo.GENERICO,
            NotificacaoCategoria.INFORMATIVA, hash, mensagem, DIGEST_TITULO);

        if (ok) {
            digestBufferRepository.deleteByUsuarioIdAndDataRef(usuarioId, dataRef);
        }
        return ok;
    }

    private boolean enfileirarInformativa(NotificacaoSolicitacao req, String hash) {
        NotificacaoDigestBuffer buf = new NotificacaoDigestBuffer();
        buf.setUsuarioId(req.getUsuarioId());
        buf.setDataRef(AppTimeZone.hoje());
        buf.setTipo(req.getEvento().name());
        buf.setHashEvento(hash);
        buf.setLinhaDigest(req.getDigestLinha());
        buf.setMensagemCompleta(req.getMensagem());
        buf.setTituloWeb(req.getTituloWeb());
        buf.setCriadoEm(AppTimeZone.agora());
        digestBufferRepository.save(buf);

        List<NotificacaoDigestBuffer> hoje = digestBufferRepository
            .findByUsuarioIdAndDataRefOrderByCriadoEmAsc(req.getUsuarioId(), AppTimeZone.hoje());

        if (hoje.size() >= 2) {
            return flushDigestUsuario(req.getUsuarioId(), AppTimeZone.hoje());
        }

        log.debug("[Orquestrador] Informativa bufferizada userId={} evento={} totalHoje={}",
            req.getUsuarioId(), req.getEvento(), hoje.size());
        return true;
    }

    private boolean entregar(
        Long usuarioId,
        NotificacaoEventoTipo evento,
        NotificacaoCategoria categoria,
        String hash,
        String mensagem,
        String tituloWeb
    ) {
        NotificacaoCanalEntrega canal = preferenciasService.obterCanalEntrega(usuarioId);
        boolean whatsappOk = false;
        boolean webOk = false;

        if (canal == NotificacaoCanalEntrega.WHATSAPP || canal == NotificacaoCanalEntrega.AMBOS) {
            whatsappOk = whatsAppDeliveryService.enviar(usuarioId, mensagem);
        }
        if (canal == NotificacaoCanalEntrega.WEB || canal == NotificacaoCanalEntrega.AMBOS) {
            webOk = webNotificationDeliveryService.enviar(
                usuarioId, tituloWeb, mensagem, categoria.name());
        }

        if (!whatsappOk && !webOk) {
            return false;
        }

        registrarEnvio(usuarioId, evento, categoria, hash);
        return true;
    }

    private void registrarEnvio(
        Long usuarioId,
        NotificacaoEventoTipo evento,
        NotificacaoCategoria categoria,
        String hash
    ) {
        NotificacaoEnviada reg = new NotificacaoEnviada();
        reg.setUsuarioId(usuarioId);
        reg.setTipo(evento.name());
        reg.setCategoria(categoria);
        reg.setHashEvento(hash);
        reg.setDataEnvio(AppTimeZone.agora());
        enviadaRepository.save(reg);
    }

    private String montarDigest(List<NotificacaoDigestBuffer> itens) {
        if (itens.size() == 1 && (itens.get(0).getLinhaDigest() == null
            || itens.get(0).getLinhaDigest().isBlank())) {
            return itens.get(0).getMensagemCompleta();
        }

        StringBuilder sb = new StringBuilder("*").append(DIGEST_TITULO).append("*\n\n");
        List<String> linhas = new ArrayList<>();
        for (NotificacaoDigestBuffer item : itens) {
            if (item.getLinhaDigest() != null && !item.getLinhaDigest().isBlank()) {
                linhas.add("• " + item.getLinhaDigest());
            } else if (item.getMensagemCompleta() != null && !item.getMensagemCompleta().isBlank()) {
                linhas.add("• " + resumirLinha(item.getMensagemCompleta()));
            }
        }
        sb.append(String.join("\n", linhas));
        return sb.toString();
    }

    private static String resumirLinha(String msg) {
        String limpa = msg.replace("\n", " ").trim();
        return limpa.length() <= 120 ? limpa : limpa.substring(0, 117) + "...";
    }

    private boolean cooldownAtivo(Long usuarioId, NotificacaoCategoria categoria) {
        if (categoria == NotificacaoCategoria.CRITICA) {
            return false;
        }
        Duration janela = categoria == NotificacaoCategoria.IMPORTANTE
            ? COOLDOWN_IMPORTANTE : COOLDOWN_INFORMATIVA;

        Optional<LocalDateTime> ultimo = enviadaRepository.findUltimoEnvioPorCategoria(usuarioId, categoria);
        return ultimo.isPresent()
            && Duration.between(ultimo.get(), AppTimeZone.agora()).compareTo(janela) < 0;
    }

    private boolean preferenciaAtiva(Long usuarioId, NotificacaoEventoTipo evento) {
        if (evento.preferenciaJarvis() == null) {
            return true;
        }
        return preferenciasService.estaAtiva(usuarioId, evento.preferenciaJarvis());
    }

    private String resolverHash(NotificacaoSolicitacao req) {
        if (req.getHashEvento() != null && !req.getHashEvento().isBlank()) {
            return req.getHashEvento();
        }
        String base = req.getUsuarioId() + "|" + req.getEvento().name() + "|" + AppTimeZone.hoje();
        if (req.getDigestLinha() != null && !req.getDigestLinha().isBlank()) {
            base += "|" + req.getDigestLinha();
        } else if (req.getMensagem() != null) {
            base += "|" + req.getMensagem().hashCode();
        }
        return sha256(base);
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}
