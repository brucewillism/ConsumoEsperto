package com.consumoesperto.edith.tools;

import com.consumoesperto.edith.EdithErrorCode;
import com.consumoesperto.edith.EdithException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EdithToolRegistryTest {

    private final EdithToolRegistry registry = new EdithToolRegistry(List.of(
        new FinanceAccountsListTool(null, null),
        new FinanceTransactionsSearchTool(null, null),
        new FinanceInvoiceReadTool(null, null),
        new FinanceCardsListTool(null, null),
        new FinanceMonthSummaryTool(null, null),
        new FinanceCashflowProjectTool(null, null),
        new FinanceSubscriptionsListTool(null, null),
        new FinanceRecurringListTool(null, null),
        new FinanceCategorySummaryTool(null, null)
    ));

    @Test
    void allowlistSomenteReadOnly() {
        List<String> allowed = registry.allowedTools();
        assertTrue(allowed.contains("finance.accounts.list"));
        assertTrue(allowed.contains("finance.cards.list"));
        assertTrue(allowed.contains("finance.month.summary"));
        assertTrue(allowed.contains("finance.cashflow.project"));
        assertTrue(allowed.contains("finance.subscriptions.list"));
        assertTrue(allowed.contains("finance.recurring.list"));
        assertTrue(allowed.contains("finance.category.summary"));
        assertTrue(allowed.contains("finance.invoice.read"));
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
