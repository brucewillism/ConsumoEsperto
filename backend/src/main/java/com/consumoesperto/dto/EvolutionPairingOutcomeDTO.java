package com.consumoesperto.dto;

import lombok.Builder;
import lombok.Value;

/**
 * Resultado ao pedir pairing na Evolution API após vínculo do número no ConsumoEsperto.
 */
@Value
@Builder
public class EvolutionPairingOutcomeDTO {

    String resolvedInstanceName;

    /** Imagem já pronta para colocar em src de img — ex.: data:image/png;base64,... */
    String qrCodeDataUri;

    /** Código alfanumérico alternativo (pairing por número), quando a Evolution o devolve. */
    String pairingCode;

    /** Já pareado segundo {@code /instance/connectionState}: não há QR a mostrar. */
    boolean alreadyConnected;

    /** Falha opcional ao falar com a Evolution (o vínculo na BD já foi gravado antes). */
    String evolutionWarning;

    /** Indica haver texto útil sobre como completar pareamento mesmo sem PNG. */
    boolean hasAlternativePairingHints;
}
