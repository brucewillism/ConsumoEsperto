package com.consumoesperto.controller;

import com.consumoesperto.eco.EcoEnvelopeHolder;
import com.consumoesperto.eco.EcoExecutionPath;
import com.consumoesperto.eco.EcoException;
import com.consumoesperto.edith.LocalFinanceCapabilityService;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.AiRateLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Invocação direta de capability determinística — JWT do usuário, sem E.D.I.T.H.
 */
@RestController
@RequestMapping("/api/capabilities")
@RequiredArgsConstructor
public class CapabilityInvokeController {

    private final LocalFinanceCapabilityService localFinanceCapabilityService;
    private final AiRateLimitService aiRateLimitService;

    @PostMapping("/{id:.+}:invoke")
    public ResponseEntity<Map<String, Object>> invoke(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable("id") String id,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        if (user == null || user.getId() == null) {
            throw EcoException.unauthorized("sessão de usuário obrigatória");
        }
        aiRateLimitService.checkOrThrow(user.getId(), "capability-invoke");
        EcoEnvelopeHolder.bindUser(user.getId());
        EcoEnvelopeHolder.markWorkStart();
        EcoEnvelopeHolder.executionPath(EcoExecutionPath.INSTANT);
        EcoEnvelopeHolder.authScheme("session");

        Map<String, Object> input = extractInput(body);
        Map<String, Object> data = localFinanceCapabilityService.invoke(user.getId(), id, input);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("capability", id);
        out.put("outcome", "SUCCESS");
        out.put("execution_path", "INSTANT");
        out.put("data", data);
        EcoEnvelopeHolder.current().ifPresent(env -> out.put("trace_id", env.getTraceId()));
        return ResponseEntity.ok(out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractInput(Map<String, Object> body) {
        if (body == null) {
            return Map.of();
        }
        Object input = body.get("input");
        if (input instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return body;
    }
}
