package com.consumoesperto.edith;

import com.consumoesperto.config.EdithProperties;
import com.consumoesperto.service.WhatsAppCommandService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Gateway legado — pipeline J.A.R.V.I.S. local com providers próprios.
 * Ativo quando E.D.I.T.H. está desabilitada (seleção explícita, sem fallback oculto).
 */
@Service
public class LegacyCognitiveGateway implements CognitiveGateway {

    private final EdithProperties properties;
    private final WhatsAppCommandService whatsAppCommandService;

    public LegacyCognitiveGateway(EdithProperties properties, @Lazy WhatsAppCommandService whatsAppCommandService) {
        this.properties = properties;
        this.whatsAppCommandService = whatsAppCommandService;
    }

    @Override
    public boolean isActive() {
        return !properties.isEnabled();
    }

    @Override
    public CognitiveResponse send(CognitiveRequest request) {
        String resposta = whatsAppCommandService.processJarvisCommand(
            request.getUsuarioId(),
            request.getContent(),
            null,
            null
        );
        return CognitiveResponse.builder()
            .status("COMPLETED")
            .resultText(resposta)
            .clientRequestId(request.getClientRequestId())
            .build();
    }
}
