package com.consumoesperto.ingest.notificacao;

import com.consumoesperto.config.IngestNotificacaoProperties;
import com.consumoesperto.ingest.notificacao.dto.IngestNotificacaoRequest;
import com.consumoesperto.ingest.notificacao.parser.NotificacaoBancariaParser;
import com.consumoesperto.ingest.notificacao.security.IngestNotificacaoException;
import com.consumoesperto.model.NotificacaoBancariaRecebida;
import com.consumoesperto.repository.NotificacaoBancariaRecebidaRepository;
import com.consumoesperto.util.AppTimeZone;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class IngestNotificacaoAcceptService {

    private final NotificacaoBancariaRecebidaRepository repository;
    private final IngestNotificacaoProperties properties;
    private final NotificacaoBancariaParser parser;

    @Transactional
    public NotificacaoBancariaRecebida aceitar(Long usuarioId, IngestNotificacaoRequest request) {
        if (request == null || request.getTexto() == null || request.getTexto().isBlank()) {
            throw new IngestNotificacaoException("Campo texto é obrigatório");
        }
        String texto = sanitizar(request.getTexto(), properties.getMaxTextoChars());
        String titulo = sanitizar(request.getTitulo(), 300);
        String origem = normalizarOrigem(request.getOrigem());
        String app = parser.normalizarApp(request.getApp(), titulo);
        String idExterno = blankToNull(request.getIdExterno());
        LocalDateTime recebidoEm = toLocal(request.getRecebidoEm());
        String hash = hashDedup(usuarioId, app, texto);

        if (idExterno != null) {
            var existente = repository.findFirstByUsuarioIdAndIdExterno(usuarioId, idExterno);
            if (existente.isPresent()) {
                return existente.get();
            }
        }

        NotificacaoBancariaRecebida row = new NotificacaoBancariaRecebida();
        row.setUsuarioId(usuarioId);
        row.setOrigem(origem);
        row.setApp(app);
        row.setTitulo(titulo);
        row.setTexto(texto);
        row.setRecebidoEm(recebidoEm);
        row.setIdExterno(idExterno);
        row.setHashDedup(hash);
        row.setStatus(NotificacaoBancariaRecebida.STATUS_RECEBIDA);
        row.setCriadoEm(AppTimeZone.agora());
        row.setAvisoPendente(false);
        try {
            return repository.save(row);
        } catch (DataIntegrityViolationException dup) {
            if (idExterno != null) {
                return repository.findFirstByUsuarioIdAndIdExterno(usuarioId, idExterno).orElseThrow(() -> dup);
            }
            throw dup;
        }
    }

    static String hashDedup(Long usuarioId, String app, String texto) {
        String payload = usuarioId + "|" + (app == null ? "" : app) + "|"
            + NotificacaoBancariaParser.normalize(texto);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    static String sanitizar(String raw, int max) {
        if (raw == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t' || !Character.isISOControl(c)) {
                sb.append(c);
            }
        }
        String s = sb.toString().trim();
        if (s.length() > max) {
            return s.substring(0, max);
        }
        return s;
    }

    static String normalizarOrigem(String origem) {
        if (origem == null || origem.isBlank()) {
            return "ANDROID_MACRODROID";
        }
        String u = origem.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if ("IOS_ATALHOS".equals(u) || "IOS_SHORTCUTS".equals(u) || "IOS".equals(u)) {
            return "IOS_ATALHOS";
        }
        return "ANDROID_MACRODROID";
    }

    static String blankToNull(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        return v.trim();
    }

    static LocalDateTime toLocal(OffsetDateTime recebidoEm) {
        if (recebidoEm == null) {
            return AppTimeZone.agora();
        }
        return recebidoEm.atZoneSameInstant(AppTimeZone.BR).toLocalDateTime();
    }
}
