package com.consumoesperto.edith;

import com.consumoesperto.config.EdithProperties;
import com.consumoesperto.edith.client.EdithApiModels;
import com.consumoesperto.edith.client.EdithHttpClient;
import com.consumoesperto.model.EdithConversationLink;
import com.consumoesperto.model.EdithTaskLink;
import com.consumoesperto.exception.ResourceNotFoundException;
import com.consumoesperto.service.FaturaService;
import com.consumoesperto.repository.EdithConversationLinkRepository;
import com.consumoesperto.repository.EdithTaskLinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EdithIntegrationService {

    private final EdithProperties properties;
    private final EdithHttpClient httpClient;
    private final EdithContextRefService contextRefService;
    private final EdithConversationLinkRepository conversationLinkRepository;
    private final EdithTaskLinkRepository taskLinkRepository;
    private final FaturaService faturaService;

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public boolean isOperational() {
        return isEnabled() && httpClient.isConfigured() && !httpClient.isCircuitOpen();
    }

    public void assertEnabled() {
        if (!isEnabled()) {
            throw new EdithException(EdithErrorCode.EDITH_DISABLED, "Integração E.D.I.T.H. desabilitada");
        }
        if (!httpClient.isConfigured() || httpClient.isCircuitOpen()) {
            throw new EdithException(EdithErrorCode.EDITH_UNAVAILABLE, "E.D.I.T.H. indisponível");
        }
    }

    @Transactional
    public EdithConversationLink createConversation(Long usuarioId) {
        assertEnabled();
        EdithApiModels.ConversationResponse resp = httpClient.createConversation(properties.getApplication());
        String edithId = resp != null ? resp.resolvedId() : null;
        if (edithId == null || edithId.isBlank()) {
            throw new EdithException(EdithErrorCode.EDITH_UNAVAILABLE, "Resposta inválida ao criar conversa");
        }
        EdithConversationLink link = new EdithConversationLink(usuarioId, edithId);
        return conversationLinkRepository.save(link);
    }

    public CognitiveResponse sendMessage(
        Long usuarioId,
        String conversationId,
        String content,
        String sourceAction,
        String clientRequestId
    ) {
        return sendMessage(usuarioId, conversationId, content, sourceAction, clientRequestId, null);
    }

    @Transactional
    public CognitiveResponse sendMessage(
        Long usuarioId,
        String conversationId,
        String content,
        String sourceAction,
        String clientRequestId,
        CognitiveRequest context
    ) {
        assertEnabled();
        String resolvedClientRequestId = clientRequestId != null && !clientRequestId.isBlank()
            ? clientRequestId
            : UUID.randomUUID().toString();
        String traceId = EdithTraceContext.createOrReuse(context != null ? context.getTraceId() : null);
        String applicationId = context != null && context.getApplicationId() != null && !context.getApplicationId().isBlank()
            ? context.getApplicationId()
            : properties.getApplicationId();

        Optional<EdithTaskLink> existing = taskLinkRepository.findByUsuarioIdAndClientRequestId(usuarioId, resolvedClientRequestId);
        if (existing.isPresent()) {
            return toCognitiveResponse(existing.get(), traceId);
        }

        EdithConversationLink conv = conversationLinkRepository
            .findByEdithConversationIdAndUsuarioId(conversationId, usuarioId)
            .orElseThrow(() -> new EdithException(EdithErrorCode.CONVERSATION_NOT_FOUND, "Conversa não encontrada"));

        String contextRef = contextRefService.generate();
        Map<String, Object> edithContext = new HashMap<>();
        edithContext.put("context_ref", contextRef);
        edithContext.put("memory_scope", "CONVERSATION");
        edithContext.put("project", properties.getProject());
        edithContext.put("application_id", applicationId);
        edithContext.put("conversation_id", conv.getEdithConversationId());
        edithContext.put("trace_id", traceId);
        if (context != null) {
            if (context.getScreen() != null && !context.getScreen().isBlank()) {
                edithContext.put("screen", context.getScreen());
            }
            putOwnedEntity(usuarioId, context, edithContext);
        }

        EdithTraceContext.put(traceId, applicationId, conv.getEdithConversationId(), null, contextRef, null);
        try {
            EdithApiModels.MessageSendRequest req = new EdithApiModels.MessageSendRequest();
            req.setMessage(content);
            req.setSourceAction(sourceAction);
            req.setClientRequestId(resolvedClientRequestId);
            req.setContext(edithContext);

            EdithApiModels.MessageSubmission resp = httpClient.sendMessage(conv.getEdithConversationId(), req);
            if (resp == null || resp.getTaskId() == null) {
                throw new EdithException(EdithErrorCode.EDITH_UNAVAILABLE, "Resposta inválida ao enviar mensagem");
            }

            EdithTaskLink link = new EdithTaskLink(
                usuarioId,
                contextRef,
                conv.getEdithConversationId(),
                resp.getMessageId(),
                resp.getTaskId(),
                resp.getRequestId(),
                resolvedClientRequestId,
                sourceAction
            );
            link.setStatus(resp.getStatus() != null ? resp.getStatus() : "QUEUED");

            try {
                taskLinkRepository.save(link);
            } catch (DataIntegrityViolationException e) {
                return taskLinkRepository.findByUsuarioIdAndClientRequestId(usuarioId, resolvedClientRequestId)
                    .map(l -> toCognitiveResponse(l, traceId))
                    .orElseThrow(() -> new EdithException(EdithErrorCode.IDEMPOTENCY_CONFLICT, "Conflito de idempotência"));
            }

            conv.touch();
            conversationLinkRepository.save(conv);

            log.info("edith_message_sent conversation_id={} task_id={} request_id={} client_request_id={} source_action={} status={} trace_id={}",
                conv.getEdithConversationId(), resp.getTaskId(), resp.getRequestId(), resolvedClientRequestId, sourceAction, link.getStatus(), traceId);

            CognitiveResponse out = toCognitiveResponse(link, traceId);
            return CognitiveResponse.builder()
                .conversationId(out.getConversationId())
                .messageId(out.getMessageId())
                .taskId(out.getTaskId())
                .requestId(out.getRequestId())
                .clientRequestId(out.getClientRequestId())
                .contextRef(out.getContextRef())
                .status(out.getStatus())
                .resultText(out.getResultText())
                .mode("EDITH")
                .traceId(traceId)
                .build();
        } finally {
            EdithTraceContext.clear();
        }
    }

    private void putOwnedEntity(Long usuarioId, CognitiveRequest context, Map<String, Object> edithContext) {
        if (context.getEntityType() == null || context.getEntityType().isBlank()
            || context.getEntityId() == null || context.getEntityId().isBlank()) {
            return;
        }
        if (!"invoice".equalsIgnoreCase(context.getEntityType())
            && !"fatura".equalsIgnoreCase(context.getEntityType())) {
            return;
        }
        try {
            long id = Long.parseLong(context.getEntityId().trim());
            faturaService.buscarPorId(id, usuarioId);
            edithContext.put("entity_type", "invoice");
            edithContext.put("entity_id", String.valueOf(id));
        } catch (NumberFormatException | ResourceNotFoundException e) {
            log.warn("edith_entity_id_rejected usuarioId={} raw={}", usuarioId, context.getEntityId());
        }
    }

    public EdithTaskLink requireTaskOwned(Long usuarioId, String taskId) {
        return taskLinkRepository.findByEdithTaskIdAndUsuarioId(taskId, usuarioId)
            .orElseThrow(() -> new EdithException(EdithErrorCode.TASK_NOT_FOUND, "Task não encontrada"));
    }

    public EdithConversationLink requireConversationOwned(Long usuarioId, String conversationId) {
        return conversationLinkRepository.findByEdithConversationIdAndUsuarioId(conversationId, usuarioId)
            .orElseThrow(() -> new EdithException(EdithErrorCode.CONVERSATION_NOT_FOUND, "Conversa não encontrada"));
    }

    public EdithApiModels.TaskResponse fetchTask(Long usuarioId, String taskId) {
        requireTaskOwned(usuarioId, taskId);
        return httpClient.getTask(taskId);
    }

    public EdithHttpClient edithHttpClient() {
        return httpClient;
    }

    public void updateTaskStatus(EdithTaskLink link, String status) {
        if (status != null && !status.equals(link.getStatus())) {
            link.setStatus(normalizeStatus(status));
            taskLinkRepository.save(link);
        }
    }

    public List<EdithConversationLink> listConversations(Long usuarioId) {
        return conversationLinkRepository.findByUsuarioIdOrderByUpdatedAtDesc(usuarioId);
    }

    public List<EdithApiModels.ConversationMessage> listConversationMessages(Long usuarioId, String conversationId) {
        requireConversationOwned(usuarioId, conversationId);
        return httpClient.listMessages(conversationId);
    }

    public Optional<Long> resolveUsuarioByContextRef(String contextRef) {
        return taskLinkRepository.findByContextRef(contextRef).map(EdithTaskLink::getUsuarioId);
    }

    public String awaitTaskResult(Long usuarioId, String taskId) {
        long deadline = System.currentTimeMillis() + properties.getTaskTimeoutMs();
        while (System.currentTimeMillis() < deadline) {
            EdithApiModels.TaskResponse task = fetchTask(usuarioId, taskId);
            if (task != null) {
                String status = normalizeStatus(task.getStatus());
                if ("COMPLETED".equals(status)) {
                    return task.getResult() != null ? task.getResult() : "";
                }
                if ("FAILED".equals(status)) {
                    throw new EdithException(EdithErrorCode.TASK_FAILED, "Task falhou na E.D.I.T.H.");
                }
            }
            try {
                Thread.sleep(properties.getTaskPollIntervalMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new EdithException(EdithErrorCode.TASK_TIMEOUT, "Task interrompida");
            }
        }
        throw new EdithException(EdithErrorCode.TASK_TIMEOUT, "Timeout aguardando task");
    }

    public static String normalizeStatus(String raw) {
        if (raw == null) {
            return "QUEUED";
        }
        return raw.trim().toUpperCase();
    }

    private CognitiveResponse toCognitiveResponse(EdithTaskLink link) {
        return toCognitiveResponse(link, null);
    }

    private CognitiveResponse toCognitiveResponse(EdithTaskLink link, String traceId) {
        return CognitiveResponse.builder()
            .conversationId(link.getEdithConversationId())
            .messageId(link.getEdithMessageId())
            .taskId(link.getEdithTaskId())
            .requestId(link.getEdithRequestId())
            .clientRequestId(link.getClientRequestId())
            .contextRef(link.getContextRef())
            .status(link.getStatus())
            .mode("EDITH")
            .traceId(traceId)
            .build();
    }
}
