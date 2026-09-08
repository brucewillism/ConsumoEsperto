package com.consumoesperto.edith.tools;

import com.consumoesperto.dto.FaturaDTO;
import com.consumoesperto.eco.EcoException;
import com.consumoesperto.edith.EdithErrorCode;
import com.consumoesperto.edith.EdithException;
import com.consumoesperto.edith.EdithIntegrationService;
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
        return executeForUser(usuarioId, input);
    }

    @Override
    public Map<String, Object> executeForUser(Long usuarioId, Map<String, Object> input) {
        Long invoiceId = parseInvoiceId(input != null ? input.get("invoice_id") : null);
        if (invoiceId == null) {
            throw EcoException.invalidInput("invoice_id obrigatório");
        }

        FaturaDTO fatura = faturaService.buscarPorId(invoiceId, usuarioId);

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
            ? fatura.getTransacoes().stream().limit(20).map(FinanceInvoiceReadTool::slimItem).collect(Collectors.toList())
            : List.of();
        out.put("principais_itens", itens);
        return out;
    }

    private static Map<String, Object> slimItem(Map<String, Object> raw) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", raw.get("id"));
        m.put("descricao", raw.get("descricao") != null ? raw.get("descricao") : raw.get("description"));
        m.put("valor", raw.get("valor") != null ? raw.get("valor") : raw.get("value"));
        return m;
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
