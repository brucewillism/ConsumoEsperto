package com.consumoesperto.service;

import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Trava o bot ao número autorizado: {@code whatsappOwnerPhone} na config de IA e/ou
 * {@link com.consumoesperto.model.Usuario#getWhatsappNumero()}. O remetente é aceite se coincidir
 * com <strong>qualquer</strong> um dos dois (útil quando o “dono” na UI difere ligeiramente do JID).
 * Para BR (+55), compara também variantes com/sem o nono dígito do celular.
 */
@Service
@RequiredArgsConstructor
public class WhatsAppBotAllowlist {

    private final AiProvidersConfigService aiProvidersConfigService;
    private final UsuarioRepository usuarioRepository;

    public boolean isConfigured(Long userId) {
        if (userId == null) {
            return false;
        }
        return !allowedDigits(userId).isEmpty();
    }

    /**
     * @param jidOrPhone JID (5511...@s.whatsapp.net), Twilio {@code whatsapp:+...}, ou E.164
     */
    public boolean matchesMyNumber(String jidOrPhone, Long userId) {
        if (jidOrPhone == null || jidOrPhone.isBlank() || userId == null) {
            return false;
        }
        if (jidOrPhone.contains("@lid")) {
            return false;
        }
        String owner = ownerDigits(userId);
        String profile = profileDigits(userId);
        if (owner.isEmpty() && profile.isEmpty()) {
            return true;
        }
        String candidate = digitsFromJidLocalPart(jidOrPhone);
        if (candidate.isEmpty()) {
            return false;
        }
        if (!owner.isEmpty() && brMsisdnMatches(owner, candidate)) {
            return true;
        }
        return !profile.isEmpty() && brMsisdnMatches(profile, candidate);
    }

    /** Prioridade do “dono” na config (comportamento antigo de {@code isConfigured}). */
    private String allowedDigits(Long userId) {
        String owner = ownerDigits(userId);
        if (!owner.isEmpty()) {
            return owner;
        }
        return profileDigits(userId);
    }

    private String ownerDigits(Long userId) {
        AiProvidersConfigService.AiProvidersConfig cfg = aiProvidersConfigService.load(userId);
        return digitsOnly(cfg != null ? cfg.getWhatsappOwnerPhone() : null);
    }

    private String profileDigits(Long userId) {
        return usuarioRepository.findById(userId)
            .map(u -> digitsOnly(u.getWhatsappNumero()))
            .orElse("");
    }

    private static String digitsFromJidLocalPart(String jidOrPhone) {
        String localPart = jidOrPhone;
        int at = localPart.indexOf('@');
        if (at > 0) {
            localPart = localPart.substring(0, at);
        }
        localPart = localPart.replace("whatsapp:", "").trim();
        return digitsOnly(localPart);
    }

    private static String digitsOnly(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("\\D", "");
    }

    /**
     * Igualdade de MSISDN; se ambos começam com 55 (BR), aceita troca do nono dígito do móvel.
     */
    private static boolean brMsisdnMatches(String aDigits, String bDigits) {
        if (aDigits.equals(bDigits)) {
            return true;
        }
        if (!aDigits.startsWith("55") || !bDigits.startsWith("55")) {
            return false;
        }
        Set<String> va = br55Variants(aDigits);
        Set<String> vb = br55Variants(bDigits);
        for (String x : va) {
            if (vb.contains(x)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> br55Variants(String digits) {
        Set<String> out = new LinkedHashSet<>();
        out.add(digits);
        if (!digits.startsWith("55") || digits.length() < 12) {
            return out;
        }
        String national = digits.substring(2);
        if (national.length() == 10) {
            out.add("55" + national.substring(0, 2) + "9" + national.substring(2));
        } else if (national.length() == 11 && national.charAt(2) == '9') {
            out.add("55" + national.substring(0, 2) + national.substring(3));
        }
        return out;
    }
}
