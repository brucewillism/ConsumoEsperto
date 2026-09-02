package com.consumoesperto.edith.tools;

import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.edith.EdithErrorCode;
import com.consumoesperto.edith.EdithException;
import com.consumoesperto.edith.EdithIntegrationService;
import com.consumoesperto.service.TransacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class FinanceTransactionsSearchTool implements EdithFinanceTool {

    private static final int MAX_PERIOD_DAYS = 366;

    private final EdithIntegrationService integrationService;
    private final TransacaoService transacaoService;

    @Override
    public String name() {
        return "finance.transactions.search";
    }

    @Override
    public Map<String, Object> execute(String contextRef, Map<String, Object> input) {
        Long usuarioId = integrationService.resolveUsuarioByContextRef(contextRef)
            .orElseThrow(() -> new EdithException(EdithErrorCode.INVALID_CONTEXT_REF, "context_ref inválido"));

        LocalDate dateFrom = parseDate(input.get("date_from"), LocalDate.now().minusMonths(1));
        LocalDate dateTo = parseDate(input.get("date_to"), LocalDate.now());
        if (dateTo.isBefore(dateFrom)) {
            throw new EdithException(EdithErrorCode.FINANCE_DATA_UNAVAILABLE, "Período inválido");
        }
        if (ChronoUnit.DAYS.between(dateFrom, dateTo) > MAX_PERIOD_DAYS) {
            throw new EdithException(EdithErrorCode.FINANCE_DATA_UNAVAILABLE, "Período máximo excedido");
        }

        int limit = FinanceAccountsListTool.parseLimit(input.get("limit"), 50, 200);
        LocalDateTime inicio = dateFrom.atStartOfDay();
        LocalDateTime fim = dateTo.plusDays(1).atStartOfDay().minusNanos(1);

        List<TransacaoDTO> transacoes = transacaoService.buscarPorPeriodo(usuarioId, inicio, fim);

        Long categoryId = parseLong(input.get("category_id"));
        Long accountId = parseLong(input.get("account_id"));
        Long cardId = parseLong(input.get("card_id"));
        String type = input.get("type") != null ? String.valueOf(input.get("type")) : null;

        List<Map<String, Object>> items = transacoes.stream()
            .filter(t -> categoryId == null || categoryId.equals(t.getCategoriaId()))
            .filter(t -> accountId == null || accountId.equals(t.getContaBancariaId()))
            .filter(t -> cardId == null || cardId.equals(t.getCartaoCreditoId()))
            .filter(t -> type == null || (t.getTipoTransacao() != null && type.equalsIgnoreCase(t.getTipoTransacao().name())))
            .limit(limit)
            .map(t -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", t.getId());
                m.put("descricao", t.getDescricao());
                m.put("valor", t.getValor());
                m.put("tipo", t.getTipoTransacao() != null ? t.getTipoTransacao().name() : null);
                m.put("data", t.getDataTransacao());
                m.put("categoria", t.getCategoriaNome());
                m.put("conta_id", t.getContaBancariaId());
                m.put("cartao_id", t.getCartaoCreditoId());
                return m;
            })
            .collect(Collectors.toList());

        Map<String, Object> out = new HashMap<>();
        out.put("transacoes", items);
        out.put("total", items.size());
        out.put("date_from", dateFrom.toString());
        out.put("date_to", dateTo.toString());
        return out;
    }

    private static LocalDate parseDate(Object raw, LocalDate fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return LocalDate.parse(String.valueOf(raw));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static Long parseLong(Object raw) {
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
