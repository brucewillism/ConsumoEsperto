package com.consumoesperto.edith;

import com.consumoesperto.edith.client.EdithApiModels;
import com.consumoesperto.edith.tools.EdithToolRegistry;
import com.consumoesperto.edith.tools.EdithToolRequestDto;
import com.consumoesperto.eco.EcoEnvelopeFactory;
import com.consumoesperto.eco.EcoEnvelopeHolder;
import com.consumoesperto.eco.EcoException;
import com.consumoesperto.eco.EcoExecutionPath;
import com.consumoesperto.eco.EcoSensitivity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EdithToolBridgeService {

    private final EdithToolRegistry toolRegistry;
    private final EdithToolAuditService auditService;

    public EdithApiModels.ToolCallbackResponse handle(EdithToolRequestDto request) {
        if (request == null || request.getTool() == null || request.getTool().isBlank()) {
            throw new EdithException(EdithErrorCode.TOOL_FAILED, "Campo tool obrigatório");
        }
        if (request.getRequestId() == null || request.getRequestId().isBlank()) {
            throw new EdithException(EdithErrorCode.TOOL_FAILED, "Campo request_id obrigatório");
        }
        Map<String, Object> arguments = request.getArguments() != null ? request.getArguments() : Map.of();
        Object rawRef = arguments.get("context_ref");
        if (rawRef == null || String.valueOf(rawRef).isBlank()) {
            throw new EdithException(EdithErrorCode.INVALID_CONTEXT_REF, "arguments.context_ref obrigatório");
        }
        String contextRef = String.valueOf(rawRef);
        String tool = request.getTool();
        if (request.getEnvelope() != null) {
            EcoEnvelopeHolder.current().ifPresent(env ->
                EcoEnvelopeHolder.replace(EcoEnvelopeFactory.fromMap(request.getEnvelope(), env)));
        }
        EcoEnvelopeHolder.current().ifPresent(env ->
            EcoEnvelopeHolder.replace(env.withRaisedSensitivity(EcoSensitivity.FINANCIAL)
                .toBuilder()
                .toolCallId(env.getToolCallId() != null ? env.getToolCallId() : request.getRequestId())
                .taskId(request.getTaskId() != null ? request.getTaskId() : env.getTaskId())
                .build()));
        String traceId = EdithTraceContext.createOrReuse(null);
        EdithTraceContext.put(traceId, null, null, request.getTaskId(), contextRef, request.getRequestId());

        long start = System.nanoTime();
        try {
            Map<String, Object> result = toolRegistry.execute(tool, contextRef, arguments);
            long latencyMs = (System.nanoTime() - start) / 1_000_000L;
            EcoEnvelopeHolder.recordToolMs(latencyMs);
            EcoEnvelopeHolder.executionPath(EcoExecutionPath.INSTANT);
            EcoEnvelopeHolder.outcome("SUCCESS");
            auditService.record(traceId, request.getRequestId(), contextRef, tool, arguments, result, latencyMs, "SUCCESS");
            log.info("edith_tool_executed tool={} request_id={} context_ref={} t_tool_ms={} trace_id={}",
                tool, request.getRequestId(), maskRef(contextRef), latencyMs, traceId);
            return EdithApiModels.ToolCallbackResponse.ok(request.getRequestId(), result);
        } catch (EcoException e) {
            long latencyMs = (System.nanoTime() - start) / 1_000_000L;
            EcoEnvelopeHolder.outcome("FAILED");
            auditService.record(traceId, request.getRequestId(), contextRef, tool, arguments, null, latencyMs, e.getCode());
            throw e;
        } catch (EdithException e) {
            long latencyMs = (System.nanoTime() - start) / 1_000_000L;
            EcoEnvelopeHolder.outcome("FAILED");
            auditService.record(traceId, request.getRequestId(), contextRef, tool, arguments, null, latencyMs, e.getCode().name());
            log.warn("edith_tool_failed tool={} code={} trace_id={}", tool, e.getCode(), traceId);
            if (e.getCode() == EdithErrorCode.TOOL_NOT_ALLOWED) {
                return EdithApiModels.ToolCallbackResponse.error(request.getRequestId(), "TOOL_BRIDGE_DENIED", e.getMessage());
            }
            throw e;
        } catch (Exception e) {
            long latencyMs = (System.nanoTime() - start) / 1_000_000L;
            EcoEnvelopeHolder.outcome("FAILED");
            auditService.record(traceId, request.getRequestId(), contextRef, tool, arguments, null, latencyMs, "FAILED");
            log.warn("edith_tool_failed tool={} error={} trace_id={}", tool, e.getClass().getSimpleName(), traceId);
            throw new EdithException(EdithErrorCode.TOOL_FAILED, "Falha ao executar tool");
        }
    }

    private static String maskRef(String ref) {
        if (ref == null || ref.length() < 8) {
            return "***";
        }
        return ref.substring(0, 4) + "..." + ref.substring(ref.length() - 4);
    }
}
