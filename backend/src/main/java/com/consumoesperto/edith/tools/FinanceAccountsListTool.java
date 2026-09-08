package com.consumoesperto.edith.tools;

import com.consumoesperto.dto.ContaBancariaDTO;
import com.consumoesperto.edith.EdithErrorCode;
import com.consumoesperto.edith.EdithException;
import com.consumoesperto.edith.EdithIntegrationService;
import com.consumoesperto.service.ContaBancariaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class FinanceAccountsListTool implements EdithFinanceTool {

    private final EdithIntegrationService integrationService;
    private final ContaBancariaService contaBancariaService;

    @Override
    public String name() {
        return "finance.accounts.list";
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
        boolean includeInactive = Boolean.TRUE.equals(args.get("include_inactive"));
        int limit = ToolLimits.require(args.get("limit"), ToolLimits.LIST_DEFAULT, ToolLimits.LIST_MAX);

        List<ContaBancariaDTO> contas = contaBancariaService.listarPorUsuario(usuarioId, !includeInactive);
        List<Map<String, Object>> items = contas.stream()
            .limit(limit)
            .map(c -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", c.getId());
                m.put("nome", c.getNome());
                m.put("tipo", c.getTipo() != null ? c.getTipo().name() : null);
                m.put("ativa", c.isAtiva());
                m.put("padrao", c.isPadrao());
                m.put("saldo_disponivel", c.getSaldoDisponivel());
                return m;
            })
            .collect(Collectors.toList());

        Map<String, Object> out = new HashMap<>();
        out.put("contas", items);
        out.put("total", items.size());
        return out;
    }

    /** @deprecated use {@link ToolLimits#require} */
    static int parseLimit(Object raw, int defaultVal, int max) {
        return ToolLimits.require(raw, defaultVal, max);
    }
}
