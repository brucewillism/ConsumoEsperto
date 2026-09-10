package com.consumoesperto.edith.tools;

import com.consumoesperto.dto.FaturaDTO;
import com.consumoesperto.eco.EcoException;
import com.consumoesperto.edith.EdithErrorCode;
import com.consumoesperto.edith.EdithException;
import com.consumoesperto.edith.EdithIntegrationService;
import com.consumoesperto.edith.UntrustedText;
import com.consumoesperto.service.FaturaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class FinanceInvoiceReadTool implements EdithFinanceTool {

    private final EdithIntegrationService integrationService;
    private final FaturaService faturaService;

    @Override
    public String name() {
        return "finance.invoice.read";
    }

    @Override
    public Map<String, Object> execute(String contextRef, Map<String, Object> input) {
        Long usuarioId = integrationService.resolveUsuarioByContextRef(contextRef)
            .orElseThrow(() -> new EdithException(EdithErrorCode.INVALID_CONTEXT_REF, "context_ref inválido"));
        return executeForUser(usuarioId, input);
    }

    @Override
    public Map<String, Object> executeForUser(Long usuarioId, Map<String, Object> input) {
        Long invoiceId = parseInvoiceId(input != null ? input.get("invoice_id") : null);
        if (invoiceId == null) {
            throw EcoException.invalidInput("invoice_id obrigatório");
        }

        FaturaDTO fatura = faturaService.buscarPorId(invoiceId, usuarioId);
        int itemCount = fatura.getTransacoes() != null ? fatura.getTransacoes().size() : 0;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", fatura.getId());
        out.put("cartao", UntrustedText.of(fatura.getNomeCartao(), 80));
        out.put("competencia", UntrustedText.of(fatura.getNumeroFatura(), 40));
        out.put("status", fatura.getStatusFatura() != null ? fatura.getStatusFatura().name() : fatura.getStatus());
        out.put("valor_total", fatura.getValorTotal() != null ? fatura.getValorTotal() : fatura.getValorFatura());
        out.put("valor_minimo", fatura.getValorMinimo());
        out.put("vencimento", fatura.getDataVencimento());
        out.put("fechamento", fatura.getDataFechamento());
        out.put("paga", fatura.getPaga());
        out.put("data_pagamento", fatura.getDataPagamento());
        out.put("valor_pago", fatura.getValorPago());
        out.put("item_count", itemCount);
        return out;
    }

    private static Long parseInvoiceId(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return raw instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(raw));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
