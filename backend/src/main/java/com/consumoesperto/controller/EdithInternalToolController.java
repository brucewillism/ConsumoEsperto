package com.consumoesperto.controller;

import com.consumoesperto.config.EdithProperties;
import com.consumoesperto.edith.EdithCallbackSecurityService;
import com.consumoesperto.edith.EdithErrorCode;
import com.consumoesperto.edith.EdithException;
import com.consumoesperto.edith.EdithToolBridgeService;
import com.consumoesperto.edith.client.EdithApiModels;
import com.consumoesperto.edith.tools.EdithToolRequestDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.consumoesperto.edith.security.EdithCallbackHeaders;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tool Bridge interno — {@code POST /api/internal/edith/tools} com HMAC E.D.I.T.H.
 */
@RestController
@RequestMapping("/api/internal/edith")
@RequiredArgsConstructor
@Slf4j
public class EdithInternalToolController {

    private final EdithProperties properties;
    private final EdithCallbackSecurityService securityService;
    private final EdithToolBridgeService toolBridgeService;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, AtomicInteger> rateByIp = new ConcurrentHashMap<>();

    @PostMapping("/tools")
    public ResponseEntity<EdithApiModels.ToolCallbackResponse> invokeTool(
        HttpServletRequest request,
        @RequestHeader(value = EdithCallbackHeaders.TIMESTAMP, required = false) String timestamp,
        @RequestHeader(value = EdithCallbackHeaders.NONCE, required = false) String nonce,
        @RequestHeader(value = EdithCallbackHeaders.SIGNATURE, required = false) String signature,
        @RequestHeader(value = EdithCallbackHeaders.REQUEST_ID, required = false) String requestId,
        @RequestBody byte[] rawBody
    ) {
        if (rawBody != null && rawBody.length > properties.getCallbackMaxBodyBytes()) {
            throw new EdithException(EdithErrorCode.TOOL_FAILED, "Payload excessivo");
        }
        enforceRateLimit(clientIp(request));
        securityService.validate(timestamp, nonce, requestId, rawBody, signature);
        EdithToolRequestDto dto = parseBody(rawBody);
        if (dto.getRequestId() == null || dto.getRequestId().isBlank()) {
            dto.setRequestId(requestId);
        }
        return ResponseEntity.ok(toolBridgeService.handle(dto));
    }

    private EdithToolRequestDto parseBody(byte[] rawBody) {
        try {
            if (rawBody == null || rawBody.length == 0) {
                throw new EdithException(EdithErrorCode.TOOL_FAILED, "Body obrigatório");
            }
            return objectMapper.readValue(rawBody, EdithToolRequestDto.class);
        } catch (EdithException e) {
            throw e;
        } catch (Exception e) {
            throw new EdithException(EdithErrorCode.TOOL_FAILED, "Schema inválido");
        }
    }

    private void enforceRateLimit(String ip) {
        AtomicInteger counter = rateByIp.computeIfAbsent(ip, k -> new AtomicInteger(0));
        if (counter.incrementAndGet() > properties.getCallbackRateLimitPerMinute()) {
            throw new EdithException(EdithErrorCode.EDITH_RATE_LIMITED, "Rate limit callback");
        }
        if (counter.get() == 1) {
            Thread t = new Thread(() -> {
                try {
                    Thread.sleep(60_000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                rateByIp.remove(ip);
            }, "edith-callback-rate-reset");
            t.setDaemon(true);
            t.start();
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
