package com.consumoesperto.edith.tools;

import com.consumoesperto.eco.EcoException;
import com.consumoesperto.edith.EdithErrorCode;
import com.consumoesperto.edith.EdithException;
import com.consumoesperto.edith.EdithIntegrationService;
import com.consumoesperto.service.TransacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class FinanceMonthSummaryTool implements EdithFinanceTool {

    private final EdithIntegrationService integrationService;
    private final TransacaoService transacaoService;

    @Override
    public String name() {
        return "finance.month.summary";
    }

    @Override
    public Map<String, Object> execute(String contextRef, Map<String, Object> input) {
        Long usuarioId = integrationService.resolveUsuarioByContextRef(contextRef)
            .orElseThrow(() -> new EdithException(EdithErrorCode.INVALID_CONTEXT_REF, "context_ref inválido"));
        return executeForUser(usuarioId, input);
    }

    @Override
    public Map<String, Object> executeForUser(Long usuarioId, Map<String, Object> input) {
        YearMonth ym = parseYearMonth(input != null ? input.get("year_month") : null);
        Map<String, Object> resumo = transacaoService.resumoFinanceiroMes(usuarioId, ym, true);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("competencia", ym.toString());
        out.put("total_receitas", resumo.get("totalReceitas"));
        out.put("total_despesas", resumo.get("totalDespesas"));
        out.put("total_investimentos", resumo.get("totalInvestimentos"));
        out.put("fluxo_mes", resumo.get("fluxoMes"));
        out.put("saldo", resumo.get("saldo"));
        out.put("saldo_projetado_fim_mes", resumo.get("saldoProjetadoFimMes"));
        out.put("total_transacoes", resumo.get("totalTransacoes"));
        return out;
    }

    static YearMonth parseYearMonth(Object raw) {
        if (raw == null) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(String.valueOf(raw));
        } catch (Exception e) {
            throw EcoException.invalidInput("year_month inválido");
        }
    }
}
