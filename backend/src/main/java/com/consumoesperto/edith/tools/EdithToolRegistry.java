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

    public EdithToolRegistry(
        FinanceAccountsListTool accountsListTool,
        FinanceTransactionsSearchTool transactionsSearchTool,
        FinanceInvoiceReadTool invoiceReadTool
    ) {
        Map<String, EdithFinanceTool> map = new LinkedHashMap<>();
        register(map, accountsListTool);
        register(map, transactionsSearchTool);
        register(map, invoiceReadTool);
        this.toolsByName = Collections.unmodifiableMap(map);
    }

    private static void register(Map<String, EdithFinanceTool> map, EdithFinanceTool tool) {
        map.put(tool.name(), tool);
    }

    public List<String> allowedTools() {
        return List.copyOf(toolsByName.keySet());
    }

    public Map<String, Object> execute(String toolName, String contextRef, Map<String, Object> input) {
        EdithFinanceTool tool = toolsByName.get(toolName);
        if (tool == null) {
            throw new EdithException(EdithErrorCode.TOOL_NOT_ALLOWED, "Tool não permitida: " + toolName);
        }
        return tool.execute(contextRef, input != null ? input : Map.of());
    }
}
