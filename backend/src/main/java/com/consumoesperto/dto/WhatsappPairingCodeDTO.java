package com.consumoesperto.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WhatsappPairingCodeDTO {

    String pairingCode;
    int validadeSegundos;
    String numeroNormalizado;
    boolean alreadyConnected;
    String evolutionWarning;
    String instanceName;
}
