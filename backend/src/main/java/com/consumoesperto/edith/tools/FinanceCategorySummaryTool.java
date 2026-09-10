package com.consumoesperto.edith.tools;

import com.consumoesperto.dto.FinanceCategoryTotalDto;
import com.consumoesperto.edith.EdithErrorCode;
import com.consumoesperto.edith.EdithException;
import com.consumoesperto.edith.EdithIntegrationService;
import com.consumoesperto.service.TransacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class FinanceCategorySummaryTool implements EdithFinanceTool {

    private final EdithIntegrationService integrationService;
    private final TransacaoService transacaoService;

    @Override
    public String name() {
        return "finance.category.summary";
    }

    @Override
    public Map<String, Object> execute(String contextRef, Map<String, Object> input) {
        Long usuarioId = integrationService.resolveUsuarioByContextRef(contextRef)
            .orElseThrow(() -> new EdithException(EdithErrorCode.INVALID_CONTEXT_REF, "context_ref inválido"));
        return executeForUser(usuarioId, input);
    }

    @Override
    public Map<String, Object> executeForUser(Long usuarioId, Map<String, Object> input) {
        YearMonth ym = FinanceMonthSummaryTool.parseYearMonth(input != null ? input.get("year_month") : null);
        int limit = ToolLimits.require(input != null ? input.get("limit") : null,
            ToolLimits.CATEGORY_DEFAULT, ToolLimits.CATEGORY_MAX);
        LocalDateTime inicio = ym.atDay(1).atStartOfDay();
        LocalDateTime fim = ym.atEndOfMonth().atTime(23, 59, 59);
        List<FinanceCategoryTotalDto> rows =
            transacaoService.agregarDespesasPorCategoriaParaCapability(usuarioId, inicio, fim, limit);
        List<Map<String, Object>> items = rows.stream().map(this::toMap).collect(Collectors.toList());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("competencia", ym.toString());
        out.put("categorias", items);
        out.put("total", items.size());
        out.put("limit", limit);
        out.put("limit_max", ToolLimits.CATEGORY_MAX);
        out.put("desde", LocalDate.from(inicio).toString());
        out.put("ate", ym.atEndOfMonth().toString());
        return out;
    }

    private Map<String, Object> toMap(FinanceCategoryTotalDto row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("category_id", row.getCategoryId());
        m.put("categoria", row.getCategoria());
        m.put("total", row.getTotal());
        return m;
    }
}
