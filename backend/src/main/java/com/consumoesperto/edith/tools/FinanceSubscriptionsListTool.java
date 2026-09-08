package com.consumoesperto.edith.tools;

import com.consumoesperto.dto.AssinaturaRecorrenteDTO;
import com.consumoesperto.edith.EdithErrorCode;
import com.consumoesperto.edith.EdithException;
import com.consumoesperto.edith.EdithIntegrationService;
import com.consumoesperto.service.AssinaturaRecorrenteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class FinanceSubscriptionsListTool implements EdithFinanceTool {

    private final EdithIntegrationService integrationService;
    private final AssinaturaRecorrenteService assinaturaRecorrenteService;

    @Override
    public String name() {
        return "finance.subscriptions.list";
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
        List<Map<String, Object>> items = assinaturaRecorrenteService.listar(usuarioId).stream()
            .limit(limit)
            .map(this::slim)
            .collect(Collectors.toList());
        Map<String, Object> out = new HashMap<>();
        out.put("assinaturas", items);
        out.put("total", items.size());
        return out;
    }

    private Map<String, Object> slim(AssinaturaRecorrenteDTO a) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", a.getId());
        m.put("nome", a.getNome());
        m.put("valor", a.getValor());
        m.put("dia_vencimento", a.getDiaVencimento());
        m.put("ativo", a.isAtivo());
        return m;
    }
}
