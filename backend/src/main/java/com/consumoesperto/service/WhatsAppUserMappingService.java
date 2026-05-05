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
     * Se o utilizador ainda não tem {@code whatsappNumero} no perfil, grava o número deste JID (primeira mensagem).
     * Evita o webhook ser ignorado com {@code owner-phone-not-configured-incoming-blocked} quando o tenant veio só da instância Evolution.
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
        if (onlyDigits.length() < 10 || onlyDigits.length() > 15) {
            throw new RuntimeException("Numero de WhatsApp invalido. Use formato internacional, ex: +5511999999999");
        }

        return "+" + onlyDigits;
    }

    private List<String> buildLookupCandidates(String normalized) {
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(normalized);

        String digits = normalized.replace("+", "");
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
