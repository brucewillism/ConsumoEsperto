package com.consumoesperto.service;

import com.consumoesperto.model.Usuario;
import com.consumoesperto.model.UsuarioAiConfig;
import com.consumoesperto.repository.UsuarioAiConfigRepository;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Cria utilizador + {@link UsuarioAiConfig} quando um número WhatsApp novo contacta o bot (opcional).
 * Ative com {@code consumoesperto.whatsapp.auto-provision-user=true}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsappAccountProvisioner {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioAiConfigRepository usuarioAiConfigRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${consumoesperto.whatsapp.auto-provision-user:false}")
    private boolean autoProvision;

    @Transactional
    public Optional<Long> provisionFromWhatsAppJid(String fromJid) {
        if (!autoProvision || fromJid == null || fromJid.isBlank()) {
            return Optional.empty();
        }
        String digits = digitsOnlyJid(fromJid);
        if (digits.length() < 10 || digits.length() > 15) {
            return Optional.empty();
        }
        String e164 = "+" + digits;
        Optional<Usuario> existing = usuarioRepository.findByWhatsappNumero(e164);
        if (existing.isPresent()) {
            return Optional.of(existing.get().getId());
        }
        Usuario u = new Usuario();
        String username = ("wa_" + digits);
        if (username.length() > 50) {
            username = username.substring(0, 50);
        }
        u.setUsername(username);
        String localEmail = "w" + digits;
        if (localEmail.length() > 40) {
            localEmail = "w" + digits.substring(digits.length() - 35);
        }
        String email = (localEmail + "@wa.local");
        if (email.length() > 50) {
            email = email.substring(0, 50);
        }
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        String nomeCurto = digits.length() >= 4 ? digits.substring(digits.length() - 4) : digits;
        u.setNome("WhatsApp " + nomeCurto);
        u.setWhatsappNumero(e164);
        u.setProvedorAuth(Usuario.ProvedorAuth.LOCAL);
        u.setEmailVerificado(false);
        u.setLocale("pt_BR");
        try {
            Usuario saved = usuarioRepository.save(u);
            UsuarioAiConfig cfg = new UsuarioAiConfig();
            cfg.setUsuario(saved);
            cfg.setProviderOrderJson("[\"GROQ\",\"OPENAI\",\"OLLAMA\"]");
            usuarioAiConfigRepository.save(cfg);
            log.info("Auto-provision WhatsApp: novo usuario id={} numero={}", saved.getId(), e164);
            return Optional.of(saved.getId());
        } catch (Exception e) {
            log.warn("Auto-provision falhou para {}: {}", e164, e.getMessage());
            return Optional.empty();
        }
    }

    private static String digitsOnlyJid(String jidOrPhone) {
        String s = jidOrPhone.trim();
        int at = s.indexOf('@');
        if (at > 0) {
            s = s.substring(0, at);
        }
        s = s.replace("whatsapp:", "").trim();
        return s.replaceAll("\\D", "");
    }
}
