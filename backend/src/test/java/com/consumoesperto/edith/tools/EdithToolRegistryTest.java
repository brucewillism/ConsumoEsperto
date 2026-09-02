package com.consumoesperto.edith.tools;

import com.consumoesperto.edith.EdithErrorCode;
import com.consumoesperto.edith.EdithException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EdithToolRegistryTest {

    private final EdithToolRegistry registry = new EdithToolRegistry(
        new FinanceAccountsListTool(null, null),
        new FinanceTransactionsSearchTool(null, null),
        new FinanceInvoiceReadTool(null, null)
    );

    @Test
    void allowlistSomenteReadOnly() {
        List<String> allowed = registry.allowedTools();
        assertEquals(3, allowed.size());
        assertEquals(List.of(
            "finance.accounts.list",
            "finance.transactions.search",
            "finance.invoice.read"
        ), allowed);
    }

    @Test
    void writeToolRejeitada() {
        EdithException ex = assertThrows(EdithException.class, () ->
            registry.execute("finance.transaction.create", "ctx-1", Map.of()));
        assertEquals(EdithErrorCode.TOOL_NOT_ALLOWED, ex.getCode());
    }

    @Test
    void toolDesconhecidaRejeitada() {
        EdithException ex = assertThrows(EdithException.class, () ->
            registry.execute("finance.admin.delete_all", "ctx-1", Map.of()));
        assertEquals(EdithErrorCode.TOOL_NOT_ALLOWED, ex.getCode());
    }
}
