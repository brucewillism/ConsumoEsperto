package com.consumoesperto.edith.tools;

import com.consumoesperto.dto.FaturaDTO;
import com.consumoesperto.edith.EdithErrorCode;
import com.consumoesperto.edith.EdithException;
import com.consumoesperto.edith.EdithIntegrationService;
import com.consumoesperto.exception.ResourceNotFoundException;
import com.consumoesperto.service.FaturaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

        Long invoiceId = parseInvoiceId(input.get("invoice_id"));
        if (invoiceId == null) {
            throw new EdithException(EdithErrorCode.FINANCE_DATA_UNAVAILABLE, "invoice_id obrigatório");
        }

        FaturaDTO fatura;
        try {
            fatura = faturaService.buscarPorId(invoiceId, usuarioId);
        } catch (ResourceNotFoundException e) {
            throw new EdithException(EdithErrorCode.FINANCE_RESOURCE_NOT_FOUND, "Fatura não encontrada");
        }

        Map<String, Object> out = new HashMap<>();
        out.put("id", fatura.getId());
        out.put("cartao", fatura.getNomeCartao());
        out.put("competencia", fatura.getNumeroFatura());
        out.put("status", fatura.getStatusFatura() != null ? fatura.getStatusFatura().name() : fatura.getStatus());
        out.put("valor_total", fatura.getValorTotal() != null ? fatura.getValorTotal() : fatura.getValorFatura());
        out.put("valor_minimo", fatura.getValorMinimo());
        out.put("vencimento", fatura.getDataVencimento());
        out.put("fechamento", fatura.getDataFechamento());
        out.put("paga", fatura.getPaga());
        out.put("data_pagamento", fatura.getDataPagamento());
        out.put("valor_pago", fatura.getValorPago());

        List<Map<String, Object>> itens = fatura.getTransacoes() != null
            ? fatura.getTransacoes().stream().limit(20).collect(Collectors.toList())
            : List.of();
        out.put("principais_itens", itens);
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
