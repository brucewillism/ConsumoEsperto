package com.consumoesperto.controller;

import com.consumoesperto.edith.EdithIntegrationService;
import com.consumoesperto.edith.EdithSseService;
import com.consumoesperto.model.EdithConversationLink;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.AiRateLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/edith")
@RequiredArgsConstructor
public class EdithController {

    private final EdithIntegrationService integrationService;
    private final EdithSseService sseService;
    private final AiRateLimitService aiRateLimitService;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        String state;
        if (!integrationService.isEnabled()) {
            state = "DISABLED";
        } else if (integrationService.isOperational()) {
            state = "AVAILABLE";
        } else {
            state = "UNAVAILABLE";
        }
        return ResponseEntity.ok(Map.of(
            "enabled", integrationService.isEnabled(),
            "state", state
        ));
    }

    @PostMapping("/conversations")
    public ResponseEntity<Map<String, Object>> createConversation(@AuthenticationPrincipal UserPrincipal user) {
        aiRateLimitService.checkOrThrow(user.getId(), "edith-conversation");
        EdithConversationLink link = integrationService.createConversation(user.getId());
        return ResponseEntity.ok(Map.of(
            "conversationId", link.getEdithConversationId(),
            "createdAt", link.getCreatedAt().toString()
        ));
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<Map<String, Object>>> listConversations(@AuthenticationPrincipal UserPrincipal user) {
        List<Map<String, Object>> items = integrationService.listConversations(user.getId()).stream()
            .map(c -> Map.<String, Object>of(
                "conversationId", c.getEdithConversationId(),
                "updatedAt", c.getUpdatedAt().toString()
            ))
            .collect(Collectors.toList());
        return ResponseEntity.ok(items);
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<Map<String, Object>> sendMessage(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable String conversationId,
        @RequestBody Map<String, String> body,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        aiRateLimitService.checkOrThrow(user.getId(), "edith-message");
        String content = body.getOrDefault("content", body.getOrDefault("mensagem", ""));
        String sourceAction = body.getOrDefault("sourceAction", "consumo.chat");
        String clientRequestId = idempotencyKey != null ? idempotencyKey : body.get("clientRequestId");

        var response = integrationService.sendMessage(user.getId(), conversationId, content, sourceAction, clientRequestId);
        return ResponseEntity.accepted().body(Map.of(
            "conversationId", response.getConversationId(),
            "messageId", response.getMessageId() != null ? response.getMessageId() : "",
            "taskId", response.getTaskId(),
            "requestId", response.getRequestId() != null ? response.getRequestId() : "",
            "clientRequestId", response.getClientRequestId(),
            "status", response.getStatus()
        ));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<?> listMessages(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable String conversationId
    ) {
        return ResponseEntity.ok(integrationService.listConversationMessages(user.getId(), conversationId));
    }

    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<?> getTask(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable String taskId
    ) {
        return ResponseEntity.ok(integrationService.fetchTask(user.getId(), taskId));
    }

    @GetMapping(value = "/tasks/{taskId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter taskEvents(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable String taskId
    ) {
        aiRateLimitService.checkOrThrow(user.getId(), "edith-sse");
        return sseService.subscribe(user.getId(), taskId);
    }
}
