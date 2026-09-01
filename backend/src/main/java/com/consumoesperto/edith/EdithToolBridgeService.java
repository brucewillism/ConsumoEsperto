package com.consumoesperto.edith;

import com.consumoesperto.edith.client.EdithApiModels;
import com.consumoesperto.edith.tools.EdithToolRegistry;
import com.consumoesperto.edith.tools.EdithToolRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EdithToolBridgeService {

    private final EdithToolRegistry toolRegistry;

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

        long start = System.nanoTime();
        try {
            Map<String, Object> result = toolRegistry.execute(tool, contextRef, arguments);
            long latencyMs = (System.nanoTime() - start) / 1_000_000L;
            log.info("edith_tool_executed tool={} request_id={} context_ref={} latency_ms={}",
                tool, request.getRequestId(), maskRef(contextRef), latencyMs);
            return EdithApiModels.ToolCallbackResponse.ok(request.getRequestId(), result);
        } catch (EdithException e) {
            log.warn("edith_tool_failed tool={} code={}", tool, e.getCode());
            if (e.getCode() == EdithErrorCode.TOOL_NOT_ALLOWED) {
                return EdithApiModels.ToolCallbackResponse.error(request.getRequestId(), "TOOL_BRIDGE_DENIED", e.getMessage());
            }
            throw e;
        } catch (Exception e) {
            log.warn("edith_tool_failed tool={} error={}", tool, e.getClass().getSimpleName());
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
