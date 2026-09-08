package com.consumoesperto.edith.tools;

import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.edith.EdithErrorCode;
import com.consumoesperto.edith.EdithException;
import com.consumoesperto.edith.EdithIntegrationService;
import com.consumoesperto.service.TransacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
        LocalDateTime inicio = ym.atDay(1).atStartOfDay();
        LocalDateTime fim = ym.atEndOfMonth().atTime(23, 59, 59);
        List<TransacaoDTO> txs = transacaoService.buscarPorPeriodo(usuarioId, inicio, fim);
        Map<String, BigDecimal> byCat = new HashMap<>();
        for (TransacaoDTO t : txs) {
            if (t.getTipoTransacao() == null || !"DESPESA".equalsIgnoreCase(t.getTipoTransacao().name())) {
                continue;
            }
            String nome = t.getCategoriaNome() != null && !t.getCategoriaNome().isBlank()
                ? t.getCategoriaNome()
                : "Sem categoria";
            BigDecimal v = t.getValor() != null ? t.getValor() : BigDecimal.ZERO;
            byCat.merge(nome, v, BigDecimal::add);
        }
        List<Map<String, Object>> items = byCat.entrySet().stream()
            .sorted(Map.Entry.<String, BigDecimal>comparingByValue(Comparator.reverseOrder()))
            .limit(ToolLimits.require(input != null ? input.get("limit") : null, 12, 30))
            .map(e -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("categoria", e.getKey());
                m.put("total", e.getValue());
                return m;
            })
            .collect(Collectors.toCollection(ArrayList::new));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("competencia", ym.toString());
        out.put("categorias", items);
        out.put("desde", LocalDate.from(inicio).toString());
        out.put("ate", ym.atEndOfMonth().toString());
        return out;
    }
}
