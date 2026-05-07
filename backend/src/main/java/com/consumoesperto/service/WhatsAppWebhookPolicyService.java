package com.consumoesperto.service;

import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Regras transversais do webhook WhatsApp/Evolution (modo estrito multi-inquilino).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppWebhookPolicyService {

    public enum TenantResolution {
        BY_WHATSAPP_LINK,
        BY_AUTO_PROVISION,
        BY_EVOLUTION_INSTANCE,
        UNKNOWN
    }

    private final UsuarioRepository usuarioRepository;
    private final AiProvidersConfigService aiProvidersConfigService;

    @Value("${consumoesperto.whatsapp.only-respond-owner:false}")
    private boolean onlyRespondOwner;

    /**
     * Com {@code ONLY_RESPOND_OWNER=true}, não aceita inquilino resolvido apenas pela instância Evolution
     * sem número configurado no perfil ou na config de IA (evita “escutar” toda a conta sem vínculo explícito).
     */
    public boolean allowsResolvedTenant(TenantResolution resolution, Long userId) {
        if (!onlyRespondOwner) {
            return true;
        }
        if (userId == null) {
            return false;
        }
        if (resolution != TenantResolution.BY_EVOLUTION_INSTANCE) {
            return true;
        }
        Usuario u = usuarioRepository.findById(userId).orElse(null);
        boolean linkedProfile = u != null && u.getWhatsappNumero() != null && !u.getWhatsappNumero().isBlank();
        AiProvidersConfigService.AiProvidersConfig cfg = aiProvidersConfigService.load(userId);
        String aiOwner = cfg != null ? cfg.getWhatsappOwnerPhone() : null;
        boolean aiOk = aiOwner != null && digitsOnly(aiOwner).length() >= 10;
        boolean ok = linkedProfile || aiOk;
        if (!ok) {
            log.warn("[WhatsAppFilter] Mensagem ignorada: ONLY_RESPOND_OWNER exige whatsapp no perfil ou whatsappOwnerPhone na IA (userId={}, resolucao={})",
                userId, resolution);
        }
        return ok;
    }

    private static String digitsOnly(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("\\D", "");
    }
}
