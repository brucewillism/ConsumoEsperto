package com.consumoesperto.controller;

import com.consumoesperto.config.EdithProperties;
import com.consumoesperto.edith.CognitiveGatewaySelector;
import com.consumoesperto.edith.CognitiveRequest;
import com.consumoesperto.edith.CognitiveResponse;
import com.consumoesperto.edith.EdithIntegrationService;
import com.consumoesperto.edith.EdithTraceContext;
import com.consumoesperto.eco.EcoEnvelopeHolder;
import com.consumoesperto.eco.EcoExecutionPath;
import com.consumoesperto.eco.EcoMode;
import com.consumoesperto.edith.LocalFinanceCapabilityService;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.AiRateLimitService;
import com.consumoesperto.service.JarvisProtocolService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/ia-chat")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = {"http://localhost:14200", "https://*.ngrok-free.app", "https://*.ngrok.io"})
public class WebAiChatController {

    private final CognitiveGatewaySelector cognitiveGatewaySelector;
    private final EdithProperties edithProperties;
    private final EdithIntegrationService edithIntegrationService;
    private final LocalFinanceCapabilityService localFinanceCapabilityService;
    private final JarvisProtocolService jarvisProtocolService;
    private final AiRateLimitService aiRateLimitService;

    @PostMapping
    public ResponseEntity<Map<String, String>> perguntar(
        @AuthenticationPrincipal UserPrincipal user,
        @RequestBody Map<String, String> body
    ) {
        aiRateLimitService.checkOrThrow(user.getId(), "ia-chat-web");
        EcoEnvelopeHolder.bindUser(user.getId());
        EcoEnvelopeHolder.markWorkStart();
        EcoEnvelopeHolder.authScheme("legacy");
        String mensagem = body.getOrDefault("mensagem", body.getOrDefault("content", ""));
        String capability = body.getOrDefault("capability", "");
        String screen = body.getOrDefault("screen", "dashboard");
        String entityType = body.get("entityType");
        String entityId = body.get("entityId");
        String traceId = EcoEnvelopeHolder.current()
            .map(e -> e.getTraceId())
            .filter(t -> t != null && !t.isBlank())
            .orElseGet(() -> EdithTraceContext.createOrReuse(body.get("traceId")));
        String applicationId = body.getOrDefault("applicationId", edithProperties.getApplicationId());

        Optional<String> local = localFinanceCapabilityService.tryExecute(user.getId(), mensagem, capability);
        if (local.isPresent()) {
            EcoEnvelopeHolder.executionPath(EcoExecutionPath.INSTANT);
            String assinada = jarvisProtocolService.assinaturaCondicional(user.getId(), local.get());
            return ResponseEntity.ok(bodyResposta(assinada, "LOCAL", null, null, traceId, "LOCAL"));
        }

        EcoMode ecoMode = EcoEnvelopeHolder.current().map(e -> e.getMode()).orElse(EcoMode.BALANCED);
        EcoEnvelopeHolder.executionPath(ecoMode == EcoMode.DEEP ? EcoExecutionPath.DEEP : EcoExecutionPath.FAST);

        boolean await = true;
        if (edithProperties.isEnabled() && edithIntegrationService.isOperational()) {
            await = "true".equalsIgnoreCase(body.getOrDefault("awaitCompletion", "false"));
        }

        CognitiveResponse response = cognitiveGatewaySelector.dispatch(CognitiveRequest.builder()
            .usuarioId(user.getId())
            .content(mensagem)
            .sourceAction("consumo.chat")
            .awaitCompletion(await)
            .applicationId(applicationId)
            .screen(screen)
            .entityType(entityType)
            .entityId(entityId)
            .traceId(traceId)
            .capability(capability)
            .build());

        String mode = response.getMode() != null ? response.getMode() : "LEGACY";
        String assistant = assistantLabel(mode, edithProperties.isEnabled());
        if ("EDITH".equals(mode) && !await && response.getTaskId() != null) {
            return ResponseEntity.accepted().body(bodyResposta(
                "",
                mode,
                response.getConversationId(),
                response.getTaskId(),
                response.getTraceId() != null ? response.getTraceId() : traceId,
                "ONLINE"
            ));
        }

        String texto = response.getResultText() != null ? response.getResultText() : "";
        String assinada = jarvisProtocolService.assinaturaCondicional(user.getId(), texto);
        return ResponseEntity.ok(bodyResposta(
            assinada != null ? assinada : "",
            mode,
            response.getConversationId(),
            response.getTaskId(),
            response.getTraceId() != null ? response.getTraceId() : traceId,
            assistant
        ));
    }

    private static Map<String, String> bodyResposta(
        String resposta,
        String mode,
        String conversationId,
        String taskId,
        String traceId,
        String assistant
    ) {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("resposta", resposta != null ? resposta : "");
        out.put("mode", mode != null ? mode : "");
        out.put("assistant", assistant != null ? assistant : "");
        if (conversationId != null) {
            out.put("conversationId", conversationId);
        }
        if (taskId != null) {
            out.put("taskId", taskId);
        }
        if (traceId != null) {
            out.put("traceId", traceId);
        }
        return out;
    }

    private static String assistantLabel(String mode, boolean edithOn) {
        if ("LOCAL".equals(mode) && !edithOn) {
            return "LOCAL";
        }
        if ("EDITH".equals(mode)) {
            return "ONLINE";
        }
        if ("LEGACY".equals(mode) && edithOn) {
            return "DEGRADED";
        }
        if ("DEGRADED".equals(mode)) {
            return "EDITH_UNAVAILABLE";
        }
        return "LOCAL";
    }
}
