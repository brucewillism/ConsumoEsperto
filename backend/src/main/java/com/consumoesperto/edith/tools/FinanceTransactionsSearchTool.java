package com.consumoesperto.edith.tools;

import com.consumoesperto.dto.FinanceTransactionSearchItemDto;
import com.consumoesperto.eco.EcoException;
import com.consumoesperto.edith.EdithErrorCode;
import com.consumoesperto.edith.EdithException;
import com.consumoesperto.edith.EdithIntegrationService;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.service.TransacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
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
        return executeForUser(usuarioId, input);
    }

    @Override
    public Map<String, Object> executeForUser(Long usuarioId, Map<String, Object> input) {
        Map<String, Object> args = input != null ? input : Map.of();
        LocalDate dateFrom = parseDate(args.get("date_from"), LocalDate.now().minusMonths(1));
        LocalDate dateTo = parseDate(args.get("date_to"), LocalDate.now());
        if (dateTo.isBefore(dateFrom)) {
            throw EcoException.invalidInput("Período inválido");
        }
        if (ChronoUnit.DAYS.between(dateFrom, dateTo) > MAX_PERIOD_DAYS) {
            throw EcoException.invalidInput("Período máximo excedido");
        }

        int limit = ToolLimits.require(args.get("limit"), ToolLimits.SEARCH_DEFAULT, ToolLimits.SEARCH_MAX);
        LocalDateTime inicio = dateFrom.atStartOfDay();
        LocalDateTime fim = dateTo.plusDays(1).atStartOfDay().minusNanos(1);
        Transacao.TipoTransacao tipo = parseTipo(args.get("type"));

        List<FinanceTransactionSearchItemDto> items = transacaoService.buscarParaCapability(
            usuarioId,
            inicio,
            fim,
            parseLong(args.get("category_id")),
            parseLong(args.get("account_id")),
            parseLong(args.get("card_id")),
            tipo,
            limit
        );

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("transacoes", items.stream().map(this::toMap).collect(Collectors.toList()));
        out.put("total", items.size());
        out.put("limit", limit);
        out.put("limit_max", ToolLimits.SEARCH_MAX);
        out.put("date_from", dateFrom.toString());
        out.put("date_to", dateTo.toString());
        return out;
    }

    private Map<String, Object> toMap(FinanceTransactionSearchItemDto item) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", item.getId());
        m.put("occurred_at", item.getOccurredAt());
        m.put("amount", item.getAmount());
        m.put("type", item.getType());
        m.put("category_id", item.getCategoryId());
        m.put("category", item.getCategory());
        m.put("account_id", item.getAccountId());
        m.put("card_id", item.getCardId());
        m.put("description", item.getDescription());
        return m;
    }

    private static LocalDate parseDate(Object raw, LocalDate fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return LocalDate.parse(String.valueOf(raw));
        } catch (Exception e) {
            throw EcoException.invalidInput("data inválida");
        }
    }

    private static Long parseLong(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return raw instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(raw));
        } catch (NumberFormatException e) {
            throw EcoException.invalidInput("identificador numérico inválido");
        }
    }

    private static Transacao.TipoTransacao parseTipo(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Transacao.TipoTransacao.valueOf(String.valueOf(raw).trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw EcoException.invalidInput("type inválido");
        }
    }
}
