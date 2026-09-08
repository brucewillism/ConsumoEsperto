package com.consumoesperto.edith.tools;

import com.consumoesperto.edith.EdithErrorCode;
import com.consumoesperto.edith.EdithException;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Allowlist explícita de tools read-only — sem reflexão arbitrária.
 */
@Component
public class EdithToolRegistry {

    private final Map<String, EdithFinanceTool> toolsByName;

    public EdithToolRegistry(List<EdithFinanceTool> tools) {
        Map<String, EdithFinanceTool> map = new LinkedHashMap<>();
        if (tools != null) {
            for (EdithFinanceTool tool : tools) {
                map.put(tool.name(), tool);
            }
        }
        this.toolsByName = Collections.unmodifiableMap(map);
    }

    public List<String> allowedTools() {
        return List.copyOf(toolsByName.keySet());
    }

    public Map<String, Object> execute(String toolName, String contextRef, Map<String, Object> input) {
        EdithFinanceTool tool = requireTool(toolName);
        return tool.execute(contextRef, input != null ? input : Map.of());
    }

    public Map<String, Object> executeForUser(String toolName, Long usuarioId, Map<String, Object> input) {
        EdithFinanceTool tool = requireTool(toolName);
        return tool.executeForUser(usuarioId, input != null ? input : Map.of());
    }

    private EdithFinanceTool requireTool(String toolName) {
        EdithFinanceTool tool = toolsByName.get(toolName);
        if (tool == null) {
            throw new EdithException(EdithErrorCode.TOOL_NOT_ALLOWED, "Tool não permitida: " + toolName);
        }
        return tool;
    }
}
