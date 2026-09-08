package com.consumoesperto.edith;

import com.consumoesperto.eco.EcoException;
import com.consumoesperto.edith.tools.EdithToolRegistry;
import com.consumoesperto.service.WhatsAppCommandService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Única implementação das capabilities financeiras locais (sem LLM).
 * {@code POST /api/capabilities/{id}:invoke} e a ponte {@code /api/ia-chat} usam isto.
 */
@Service
@Slf4j
public class LocalFinanceCapabilityService {

    private static final Pattern LOCAL_CMD = Pattern.compile("(?i)^(tutorial|ajuda|sair|menu)$");

    private final EdithToolRegistry toolRegistry;
    private final WhatsAppCommandService whatsAppCommandService;
    private final CapabilityManifestService capabilityManifestService;

    public LocalFinanceCapabilityService(
        EdithToolRegistry toolRegistry,
        @Lazy WhatsAppCommandService whatsAppCommandService,
        CapabilityManifestService capabilityManifestService
    ) {
        this.toolRegistry = toolRegistry;
        this.whatsAppCommandService = whatsAppCommandService;
        this.capabilityManifestService = capabilityManifestService;
    }

    public Map<String, Object> invoke(Long usuarioId, String capabilityId, Map<String, Object> input) {
        if (usuarioId == null) {
            throw EcoException.unauthorized("sessão de usuário obrigatória");
        }
        if (capabilityId == null || capabilityId.isBlank()) {
            throw EcoException.invalidInput("capability obrigatória");
        }
        String id = capabilityId.trim();
        if (!toolRegistry.allowedTools().contains(id)) {
            throw EcoException.capabilityNotFound(id);
        }
        Map<String, Object> args = input != null ? input : Map.of();
        capabilityManifestService.validateInput(id, args);
        return toolRegistry.executeForUser(id, usuarioId, args);
    }

    /**
     * Ponte {@code /api/ia-chat}: só executa capability se o cliente mandar o ID
     * ({@code auth_scheme: legacy}). Texto livre não é resolvido por regex —
     * vai para a E.D.I.T.H. em {@code BALANCED}.
     */
    public Optional<String> tryExecute(Long usuarioId, String text, String capabilityHint) {
        String resolved = resolveCapabilityId(text, capabilityHint);
        if (resolved != null) {
            Map<String, Object> data = invoke(usuarioId, resolved, Map.of());
            return Optional.of(formatConversational(resolved, data));
        }
        String raw = text != null ? text.trim() : "";
        if (LOCAL_CMD.matcher(raw).matches()) {
            return Optional.ofNullable(whatsAppCommandService.processJarvisCommand(usuarioId, raw, null, null));
        }
        return Optional.empty();
    }

    public static String resolveCapabilityId(String text, String capabilityHint) {
        String cap = capabilityHint != null ? capabilityHint.trim() : "";
        if (!cap.isBlank()) {
            return cap;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static String formatConversational(String capabilityId, Map<String, Object> data) {
        if ("finance.cards.list".equals(capabilityId)) {
            return formatCards((List<Map<String, Object>>) data.getOrDefault("cartoes", List.of()));
        }
        if ("finance.month.summary".equals(capabilityId) || "finance.cashflow.project".equals(capabilityId)) {
            return formatMonth(data);
        }
        return data != null ? data.toString() : "";
    }

    private static String formatCards(List<Map<String, Object>> cartoes) {
        if (cartoes == null || cartoes.isEmpty()) {
            return "Você não tem cartões ativos cadastrados.";
        }
        StringBuilder sb = new StringBuilder("Cartões ativos:\n");
        for (Map<String, Object> c : cartoes) {
            sb.append("• ").append(c.get("nome") != null ? c.get("nome") : "Cartão");
            if (c.get("banco") != null && !String.valueOf(c.get("banco")).isBlank()) {
                sb.append(" (").append(c.get("banco")).append(")");
            }
            if (c.get("limite_disponivel") != null) {
                sb.append(" — disponível ").append(brl(c.get("limite_disponivel")));
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    private static String formatMonth(Map<String, Object> resumo) {
        YearMonth ym = YearMonth.now();
        Object competencia = resumo.get("competencia");
        if (competencia != null) {
            try {
                ym = YearMonth.parse(String.valueOf(competencia));
            } catch (Exception ignored) {
                // mantém now
            }
        }
        String mes = ym.getMonth().getDisplayName(TextStyle.FULL, new Locale("pt", "BR")) + "/" + ym.getYear();
        StringBuilder sb = new StringBuilder("Fechamento de ").append(mes).append(":\n");
        sb.append("• Receitas: ").append(brl(first(resumo, "total_receitas", "receitas_previstas"))).append('\n');
        sb.append("• Despesas: ").append(brl(first(resumo, "total_despesas", "despesas_previstas"))).append('\n');
        if (resumo.get("fluxo_mes") != null) {
            sb.append("• Fluxo do mês: ").append(brl(resumo.get("fluxo_mes"))).append('\n');
        }
        if (resumo.get("saldo_projetado_fim_mes") != null) {
            sb.append("• Saldo projetado no fim do mês: ").append(brl(resumo.get("saldo_projetado_fim_mes"))).append('\n');
        }
        if (resumo.get("saldo_previsto") != null) {
            sb.append("• Saldo previsto: ").append(brl(resumo.get("saldo_previsto"))).append('\n');
        }
        if (resumo.get("explicacao") != null) {
            sb.append('\n').append(resumo.get("explicacao"));
        }
        return sb.toString().trim();
    }

    private static Object first(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            if (map.get(k) != null) {
                return map.get(k);
            }
        }
        return null;
    }

    private static String brl(Object value) {
        BigDecimal n = BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) {
            n = bd;
        } else if (value instanceof Number num) {
            n = BigDecimal.valueOf(num.doubleValue());
        }
        return NumberFormat.getCurrencyInstance(new Locale("pt", "BR")).format(n);
    }
}
