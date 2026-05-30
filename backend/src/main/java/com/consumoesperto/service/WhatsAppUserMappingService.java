package com.consumoesperto.service;

import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppUserMappingService {

    private final UsuarioRepository usuarioRepository;

    public Optional<Usuario> findByIncomingNumber(String incomingNumber) {
        String normalized = normalize(incomingNumber);
        for (String candidate : buildLookupCandidates(normalized)) {
            Optional<Usuario> usuario = usuarioRepository.findByWhatsappNumero(candidate);
            if (usuario.isPresent()) {
                return usuario;
            }
        }
        return Optional.empty();
    }

    @Transactional
    public Usuario linkWhatsAppNumber(Long usuarioId, String incomingNumber) {
        String normalized = normalize(incomingNumber);

        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new RuntimeException("Usuario nao encontrado"));

        Optional<Usuario> existing = usuarioRepository.findByWhatsappNumero(normalized);
        if (existing.isPresent() && !existing.get().getId().equals(usuarioId)) {
            throw new RuntimeException("Numero de WhatsApp ja esta vinculado a outro usuario");
        }

        usuario.setWhatsappNumero(normalized);
        return usuarioRepository.save(usuario);
    }

    /**
     * Se o utilizador ainda não tem {@code whatsappNumero} no perfil, grava o número deste JID após o webhook
     * já ter validado que o remetente é o autorizado ({@code WhatsAppBotAllowlist}). Chamado só depois do filtro no
     * {@link com.consumoesperto.controller.EvolutionWebhookController}.
     */
    @Transactional
    public void ensureLinkedIfEmpty(Long usuarioId, String fromJid) {
        if (usuarioId == null || fromJid == null || fromJid.isBlank()) {
            return;
        }
        if (fromJid.endsWith("@g.us") || fromJid.endsWith("@broadcast") || fromJid.equalsIgnoreCase("status@broadcast")) {
            return;
        }
        if (fromJid.contains("@lid")) {
            return;
        }
        Usuario u = usuarioRepository.findById(usuarioId).orElse(null);
        if (u == null) {
            return;
        }
        if (u.getWhatsappNumero() != null && !u.getWhatsappNumero().isBlank()) {
            return;
        }
        try {
            linkWhatsAppNumber(usuarioId, fromJid);
            log.info("WhatsApp auto-vinculado ao utilizador id={} a partir do primeiro contacto", usuarioId);
        } catch (Exception e) {
            log.warn("Auto-vinculo WhatsApp ignorado para userId={}: {}", usuarioId, e.getMessage());
        }
    }

    @Transactional
    public Optional<String> repairStoredWhatsappNumeroIfNeeded(Long usuarioId) {
        if (usuarioId == null) {
            return Optional.empty();
        }
        Usuario usuario = usuarioRepository.findById(usuarioId).orElse(null);
        if (usuario == null) {
            return Optional.empty();
        }
        String stored = usuario.getWhatsappNumero();
        if (stored == null || stored.isBlank()) {
            return Optional.empty();
        }
        try {
            String fixed = normalize(stored);
            if (!fixed.equals(stored)) {
                usuario.setWhatsappNumero(fixed);
                usuarioRepository.save(usuario);
                log.info("WhatsApp do utilizador id={} corrigido de {} para {}", usuarioId, stored, fixed);
            }
            return Optional.of(fixed);
        } catch (RuntimeException e) {
            log.debug("Correcção automática do número WhatsApp ignorada (userId={}): {}", usuarioId, e.getMessage());
            return Optional.of(stored);
        }
    }

    @Transactional
    public Usuario unlinkWhatsAppNumber(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new RuntimeException("Usuario nao encontrado"));

        usuario.setWhatsappNumero(null);
        return usuarioRepository.save(usuario);
    }

    public String normalize(String number) {
        if (number == null || number.isBlank()) {
            throw new RuntimeException("Numero de WhatsApp vazio");
        }

        String cleaned = number.trim().toLowerCase();
        if (cleaned.startsWith("whatsapp:")) {
            cleaned = cleaned.substring("whatsapp:".length());
        }

        String digits = cleaned.replaceAll("[^0-9+]", "");
        if (!digits.startsWith("+")) {
            digits = "+" + digits.replace("+", "");
        }

        String onlyDigits = digits.replace("+", "");
        onlyDigits = applyBrazilCountryCodeIfNational(onlyDigits);
        if (onlyDigits.length() < 10 || onlyDigits.length() > 15) {
            throw new RuntimeException("Numero de WhatsApp invalido. Use formato internacional, ex: +5511999999999");
        }

        return "+" + onlyDigits;
    }

    /**
     * Números BR sem código do país (ex. 81986561809 ou +81986561809) são tratados como DDD+número, não como +81 (Japão).
     */
    static String applyBrazilCountryCodeIfNational(String onlyDigits) {
        if (onlyDigits == null || onlyDigits.isBlank()) {
            return onlyDigits;
        }
        String d = onlyDigits.replace("+", "").trim();
        if (d.startsWith("55")) {
            return d;
        }
        if (d.length() == 10 || d.length() == 11) {
            return "55" + d;
        }
        return d;
    }

    private List<String> buildLookupCandidates(String normalized) {
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(normalized);

        String digits = normalized.replace("+", "");
        if (!digits.startsWith("55") && (digits.length() == 10 || digits.length() == 11)) {
            candidates.add("+55" + digits);
        }
        // BR: +55 + DDD(2) + numero(8|9). Algumas fontes removem/adicionam o nono digito.
        if (digits.startsWith("55")) {
            String national = digits.substring(2);
            if (national.length() == 10) {
                String withNinth = national.substring(0, 2) + "9" + national.substring(2);
                candidates.add("+55" + withNinth);
            } else if (national.length() == 11 && national.charAt(2) == '9') {
                String withoutNinth = national.substring(0, 2) + national.substring(3);
                candidates.add("+55" + withoutNinth);
            }
        }

        return new ArrayList<>(candidates);
    }
}
