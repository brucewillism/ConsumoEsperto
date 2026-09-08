package com.consumoesperto.edith.tools;

import com.consumoesperto.dto.AgendamentoPagamentoDTO;
import com.consumoesperto.edith.EdithErrorCode;
import com.consumoesperto.edith.EdithException;
import com.consumoesperto.edith.EdithIntegrationService;
import com.consumoesperto.service.AgendamentoPagamentoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class FinanceRecurringListTool implements EdithFinanceTool {

    private final EdithIntegrationService integrationService;
    private final AgendamentoPagamentoService agendamentoPagamentoService;

    @Override
    public String name() {
        return "finance.recurring.list";
    }

    @Override
    public Map<String, Object> execute(String contextRef, Map<String, Object> input) {
        Long usuarioId = integrationService.resolveUsuarioByContextRef(contextRef)
            .orElseThrow(() -> new EdithException(EdithErrorCode.INVALID_CONTEXT_REF, "context_ref inválido"));
        return executeForUser(usuarioId, input);
    }

    @Override
    public Map<String, Object> executeForUser(Long usuarioId, Map<String, Object> input) {
        int limit = ToolLimits.require(input != null ? input.get("limit") : null, ToolLimits.LIST_DEFAULT, ToolLimits.LIST_MAX);
        List<Map<String, Object>> items = agendamentoPagamentoService.listar(usuarioId).stream()
            .limit(limit)
            .map(this::slim)
            .collect(Collectors.toList());
        Map<String, Object> out = new HashMap<>();
        out.put("agendamentos", items);
        out.put("total", items.size());
        return out;
    }

    private Map<String, Object> slim(AgendamentoPagamentoDTO a) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", a.getId());
        m.put("beneficiario", a.getBeneficiario());
        m.put("valor", a.getValor());
        m.put("vencimento", a.getDataVencimento());
        m.put("status", a.getStatus());
        m.put("recorrencia", a.getRecorrencia());
        return m;
    }
}
