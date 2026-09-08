package com.consumoesperto.edith.tools;

import com.consumoesperto.dto.motor.MotorFinanceiroInteligenteDTO;
import com.consumoesperto.edith.EdithErrorCode;
import com.consumoesperto.edith.EdithException;
import com.consumoesperto.edith.EdithIntegrationService;
import com.consumoesperto.service.MotorFinanceiroService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class FinanceCashflowProjectTool implements EdithFinanceTool {

    private final EdithIntegrationService integrationService;
    private final MotorFinanceiroService motorFinanceiroService;

    @Override
    public String name() {
        return "finance.cashflow.project";
    }

    @Override
    public Map<String, Object> execute(String contextRef, Map<String, Object> input) {
        Long usuarioId = integrationService.resolveUsuarioByContextRef(contextRef)
            .orElseThrow(() -> new EdithException(EdithErrorCode.INVALID_CONTEXT_REF, "context_ref inválido"));
        return executeForUser(usuarioId, input);
    }

    @Override
    public Map<String, Object> executeForUser(Long usuarioId, Map<String, Object> input) {
        MotorFinanceiroInteligenteDTO dto = motorFinanceiroService.calcular(usuarioId, false);
        Map<String, Object> out = new LinkedHashMap<>();
        if (dto.getForecastInteligente() != null) {
            var f = dto.getForecastInteligente();
            out.put("saldo_previsto", f.getSaldoPrevisto());
            out.put("despesas_previstas", f.getDespesasPrevistas());
            out.put("receitas_previstas", f.getReceitasPrevistas());
            out.put("chance_mes_positivo_pct", f.getChanceMesPositivoPct());
            out.put("explicacao", f.getExplicacaoDeterministica());
        }
        out.put("calculado_em", dto.getCalculadoEm());
        return out;
    }
}
