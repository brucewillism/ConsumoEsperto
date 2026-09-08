package com.consumoesperto.edith.tools;

import com.consumoesperto.dto.CartaoCreditoDTO;
import com.consumoesperto.edith.EdithErrorCode;
import com.consumoesperto.edith.EdithException;
import com.consumoesperto.edith.EdithIntegrationService;
import com.consumoesperto.service.CartaoCreditoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class FinanceCardsListTool implements EdithFinanceTool {

    private final EdithIntegrationService integrationService;
    private final CartaoCreditoService cartaoCreditoService;

    @Override
    public String name() {
        return "finance.cards.list";
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
        List<Map<String, Object>> items = cartaoCreditoService.buscarPorUsuarioId(usuarioId).stream()
            .limit(limit)
            .map(this::slim)
            .collect(Collectors.toList());
        Map<String, Object> out = new HashMap<>();
        out.put("cartoes", items);
        out.put("total", items.size());
        return out;
    }

    private Map<String, Object> slim(CartaoCreditoDTO c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("nome", c.getNome());
        m.put("banco", c.getBanco());
        m.put("limite_disponivel", c.getLimiteDisponivel());
        m.put("dia_vencimento", c.getDiaVencimento());
        m.put("ativo", c.getAtivo());
        return m;
    }
}
