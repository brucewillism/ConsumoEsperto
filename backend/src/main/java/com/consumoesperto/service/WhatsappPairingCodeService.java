package com.consumoesperto.service;

import com.consumoesperto.config.WhatsappConexaoMonitorProperties;
import com.consumoesperto.dto.EvolutionPairingOutcomeDTO;
import com.consumoesperto.dto.WhatsappPairingCodeDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Pareamento Evolution por código de 8 dígitos ({@code GET /instance/connect?number=}).
 */
@Service
@RequiredArgsConstructor
public class WhatsappPairingCodeService {

    private final WhatsAppUserMappingService whatsAppUserMappingService;
    private final EvolutionPairingService evolutionPairingService;
    private final EvolutionInstanceLifecycleService evolutionInstanceLifecycleService;
    private final WhatsappConexaoMonitorProperties monitorProperties;

    public WhatsappPairingCodeDTO gerar(Long usuarioId, String numeroBruto) {
        if (usuarioId == null) {
            throw new IllegalArgumentException("Utilizador não autenticado");
        }
        String e164 = whatsAppUserMappingService.normalize(numeroBruto);
        String digits = e164.replace("+", "");

        evolutionPairingService.clearWaSessionDisconnectedByUser(usuarioId);
        whatsAppUserMappingService.linkWhatsAppNumber(usuarioId, e164);
        evolutionInstanceLifecycleService.prepareInstanceForPairing(usuarioId);
        evolutionInstanceLifecycleService.primeInstanceForQrConnect(usuarioId);
        evolutionPairingService.invalidatePairingCredCache(usuarioId);

        EvolutionPairingOutcomeDTO pairing = evolutionPairingService.invokeInstanceConnect(usuarioId);
        boolean waConnected = evolutionPairingService.isInstanceConnectedForUser(usuarioId);
        if (waConnected) {
            evolutionPairingService.clearWaSessionDisconnectedByUser(usuarioId);
        }

        String code = pairing.getPairingCode();
        String warn = pairing.getEvolutionWarning();
        if (!waConnected && (code == null || code.isBlank())) {
            String msg = (warn != null && !warn.isBlank())
                ? warn
                : "A Evolution não devolveu código de pareamento. Use a aba «Escanear QR» "
                    + "ou abra o Manager da Evolution.";
            throw new PairingCodeIndisponivelException(msg);
        }

        return WhatsappPairingCodeDTO.builder()
            .pairingCode(code)
            .validadeSegundos(Math.max(30, monitorProperties.getPairingCodeTtlSegundos()))
            .numeroNormalizado(e164)
            .alreadyConnected(waConnected)
            .evolutionWarning(warn)
            .instanceName(pairing.getResolvedInstanceName())
            .build();
    }

    public static final class PairingCodeIndisponivelException extends RuntimeException {
        public PairingCodeIndisponivelException(String message) {
            super(message);
        }
    }
}
