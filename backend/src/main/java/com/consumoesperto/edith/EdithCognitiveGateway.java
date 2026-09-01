package com.consumoesperto.edith;

import com.consumoesperto.config.EdithProperties;
import com.consumoesperto.model.EdithConversationLink;
import com.consumoesperto.repository.EdithConversationLinkRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Gateway cognitivo via E.D.I.T.H. — ativo somente quando {@code consumoesperto.edith.enabled=true}.
 */
@Service
public class EdithCognitiveGateway implements CognitiveGateway {

    private final EdithProperties properties;
    private final EdithIntegrationService integrationService;
    private final EdithConversationLinkRepository conversationLinkRepository;

    public EdithCognitiveGateway(
        EdithProperties properties,
        EdithIntegrationService integrationService,
        EdithConversationLinkRepository conversationLinkRepository
    ) {
        this.properties = properties;
        this.integrationService = integrationService;
        this.conversationLinkRepository = conversationLinkRepository;
    }

    @Override
    public boolean isActive() {
        return properties.isEnabled();
    }

    @Override
    public CognitiveResponse send(CognitiveRequest request) {
        integrationService.assertEnabled();
        String conversationId = request.getConversationId();
        if (conversationId == null || conversationId.isBlank()) {
            List<EdithConversationLink> existing = conversationLinkRepository
                .findByUsuarioIdOrderByUpdatedAtDesc(request.getUsuarioId());
            if (!existing.isEmpty()) {
                conversationId = existing.get(0).getEdithConversationId();
            } else {
                EdithConversationLink created = integrationService.createConversation(request.getUsuarioId());
                conversationId = created.getEdithConversationId();
            }
        }
        CognitiveResponse response = integrationService.sendMessage(
            request.getUsuarioId(),
            conversationId,
            request.getContent(),
            request.getSourceAction() != null ? request.getSourceAction() : "consumo.chat",
            request.getClientRequestId()
        );
        if (request.isAwaitCompletion()) {
            String result = integrationService.awaitTaskResult(request.getUsuarioId(), response.getTaskId());
            return CognitiveResponse.builder()
                .conversationId(response.getConversationId())
                .messageId(response.getMessageId())
                .taskId(response.getTaskId())
                .requestId(response.getRequestId())
                .clientRequestId(response.getClientRequestId())
                .contextRef(response.getContextRef())
                .status("COMPLETED")
                .resultText(result)
                .build();
        }
        return response;
    }
}
