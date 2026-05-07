package com.consumoesperto.service;

import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Trava o bot ao número autorizado: {@code whatsappOwnerPhone} na config de IA e/ou
 * {@link com.consumoesperto.model.Usuario#getWhatsappNumero()}. O remetente é aceite se coincidir
 * com <strong>qualquer</strong> um dos dois (útil quando o “dono” na UI difere ligeiramente do JID).
 * Para BR (+55), compara também variantes com/sem o nono dígito do celular.
 * Opcionalmente aceita números extra via {@code consumoesperto.whatsapp.authorized-numbers-extra}
 * e {@code consumoesperto.whatsapp.owner-phone-global}.
 * Se ambos estiverem vazios, {@link #matchesMyNumber(String, Long)} devolve {@code false} (nenhum remetente é aceite).
 */
@Service
@RequiredArgsConstructor
public class WhatsAppBotAllowlist {

    private final AiProvidersConfigService aiProvidersConfigService;
    private final UsuarioRepository usuarioRepository;

    @Value("${consumoesperto.whatsapp.authorized-numbers-extra:}")
    private String authorizedNumbersExtraRaw;

    @Value("${consumoesperto.whatsapp.owner-phone-global:}")
    private String ownerPhoneGlobalRaw;

    private volatile Set<String> authorizedExtraDigits = Collections.emptySet();
    private volatile String globalOwnerDigits = "";

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
        // Sem número autorizado configurado: não aceitar nenhum JID como “meu número”.
        // Antes retornava true e, com tenant resolvido só pela instância Evolution, o bot respondia a qualquer chat.
        if (owner.isEmpty() && profile.isEmpty()) {
            return false;
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

    @PostConstruct
    public void refreshAuthorizedListsFromProperties() {
        globalOwnerDigits = digitsOnly(ownerPhoneGlobalRaw);
        Set<String> extras = new LinkedHashSet<>();
        if (authorizedNumbersExtraRaw != null && !authorizedNumbersExtraRaw.isBlank()) {
            for (String part : authorizedNumbersExtraRaw.split(",")) {
                String d = digitsOnly(part.trim());
                if (d.length() >= 10) {
                    extras.add(d);
                }
            }
        }
        authorizedExtraDigits = Collections.unmodifiableSet(extras);
    }

    /**
     * Números autorizados explicitamente por propriedades (lista CSV ou telefone global do servidor).
     */
    public boolean matchesAuthorizedExtrasGlobal(String jidOrPhone) {
        if (jidOrPhone == null || jidOrPhone.isBlank() || jidOrPhone.contains("@lid")) {
            return false;
        }
        String candidate = digitsFromJidLocalPart(jidOrPhone);
        if (candidate.isEmpty()) {
            return false;
        }
        if (!globalOwnerDigits.isEmpty() && brMsisdnMatches(globalOwnerDigits, candidate)) {
            return true;
        }
        for (String e : authorizedExtraDigits) {
            if (brMsisdnMatches(e, candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Twilio (ou casos em que o campo “From” já é o número do utilizador na linha do bot).
     * Para Evolution na conversa consigo mesmo use {@link #isEvolutionSelfChatThread(String, Long)}.
     */
    public boolean isEvolutionWebhookSenderAllowed(String jidOrPhone, Long userId) {
        if (jidOrPhone == null || jidOrPhone.isBlank() || userId == null) {
            return false;
        }
        if (jidOrPhone.contains("@lid")) {
            return false;
        }
        return matchesMyNumber(jidOrPhone, userId) || matchesAuthorizedExtrasGlobal(jidOrPhone);
    }

    /**
     * Evolution API: só na conversa “eu comigo” ({@code remoteJid} = teu número).
     * Não exige {@code fromMe}: muitos webhooks Evolution/Baileys trazem {@code fromMe=false} mesmo para mensagens tuas
     * nessa conversa; em chats com terceiros {@code remoteJid} é o outro número e isto falha.
     * Ecos/linhas de resposta do bot são filtrados depois em {@code WhatsAppCommandService}.
     */
    public boolean isEvolutionSelfChatThread(String remoteJid, Long userId) {
        if (remoteJid == null || remoteJid.isBlank() || userId == null) {
            return false;
        }
        if (remoteJid.contains("@lid")) {
            return false;
        }
        return matchesMyNumber(remoteJid, userId) || matchesAuthorizedExtrasGlobal(remoteJid);
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

    /**
     * Há número “de dono” configurado (perfil ou config IA) — usado onde ainda se quer exigir config explícita
     * além dos extras globais.
     */
    public boolean hasProfileWhatsappLinked(Long userId) {
        if (userId == null) {
            return false;
        }
        if (!profileDigits(userId).isEmpty()) {
            return true;
        }
        String owner = ownerDigits(userId);
        return owner.length() >= 10;
    }

    /**
     * @deprecated Preferir {@link #isEvolutionWebhookSenderAllowed(String, Long)} ou {@link #isEvolutionSelfChatThread(String, Long)}.
     */
    @Deprecated
    public boolean matchesJarvisProfileWhatsappOnly(String jidOrPhone, Long userId) {
        if (jidOrPhone == null || jidOrPhone.isBlank() || userId == null) {
            return false;
        }
        if (jidOrPhone.contains("@lid")) {
            return false;
        }
        String profile = profileDigits(userId);
        if (profile.isEmpty()) {
            return false;
        }
        String cand = digitsFromJidLocalPart(jidOrPhone);
        if (cand.isEmpty()) {
            return false;
        }
        return brMsisdnMatches(profile, cand);
    }
}
