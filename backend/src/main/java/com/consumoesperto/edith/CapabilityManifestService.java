package com.consumoesperto.edith;

import com.consumoesperto.config.EdithProperties;
import com.consumoesperto.eco.EcoException;
import com.consumoesperto.edith.tools.EdithToolRegistry;
import com.consumoesperto.edith.tools.ToolLimits;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manifesto GET /capabilities — só o que está implementado.
 * {@code p95_latency_ms} vem de medição (docs/BASELINE_TOOLS.md), não de chute.
 */
@Component
public class CapabilityManifestService {

    private final EdithToolRegistry toolRegistry;
    private final EdithProperties edithProperties;

    public CapabilityManifestService(EdithToolRegistry toolRegistry, EdithProperties edithProperties) {
        this.toolRegistry = toolRegistry;
        this.edithProperties = edithProperties;
    }

    public Map<String, Object> manifest() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("provider", "consumo-esperto");
        root.put("manifest_version", "1");
        root.put("generated_at", Instant.now().toString());
        List<Map<String, Object>> caps = new ArrayList<>();
        for (String id : toolRegistry.allowedTools()) {
            Map<String, Object> spec = spec(id);
            if (spec != null) {
                caps.add(spec);
            }
        }
        root.put("capabilities", caps);
        return root;
    }

    public boolean serviceSecretMatches(String provided) {
        String expected = edithProperties.getCallbackSecret();
        if (expected == null || expected.isBlank() || provided == null) {
            return false;
        }
        return expected.equals(provided);
    }

    @SuppressWarnings("unchecked")
    public void validateInput(String capabilityId, Map<String, Object> input) {
        Map<String, Object> spec = spec(capabilityId);
        if (spec == null) {
            throw EcoException.capabilityNotFound(capabilityId);
        }
        Map<String, Object> schema = (Map<String, Object>) spec.get("input_schema");
        if (schema == null) {
            return;
        }
        Object requiredRaw = schema.get("required");
        if (!(requiredRaw instanceof List<?> required)) {
            return;
        }
        Map<String, Object> args = input != null ? input : Map.of();
        for (Object key : required) {
            Object value = args.get(String.valueOf(key));
            if (value == null || String.valueOf(value).isBlank()) {
                throw EcoException.invalidInput(key + " obrigatório");
            }
        }
    }

    private static Map<String, Object> spec(String id) {
        return switch (id) {
            case "finance.accounts.list" -> cap(
                id,
                "Lista contas/carteiras do usuário com tipo, status e saldo disponível.",
                List.of("listar minhas contas", "minhas carteiras", "contas bancarias", "mostrar contas", "quais contas eu tenho"),
                Map.of("type", "object", "properties", Map.of(
                    "include_inactive", Map.of("type", "boolean"),
                    "limit", Map.of("type", "integer", "maximum", ToolLimits.LIST_MAX)
                ), "required", List.of()),
                Map.of("type", "object", "properties", Map.of("contas", Map.of("type", "array"))),
                CapabilityLatencyManifest.ACCOUNTS_LIST
            );
            case "finance.transactions.search" -> cap(
                id,
                "Busca transações do usuário no período, com filtros e teto rígido de resultados no SQL.",
                List.of("buscar transacoes", "minhas transações", "extrato do periodo", "gastos recentes", "listar lancamentos", "transacoes do mes", "trasacoes", "extrato recente"),
                Map.of("type", "object", "properties", Map.of(
                    "date_from", Map.of("type", "string"),
                    "date_to", Map.of("type", "string"),
                    "limit", Map.of("type", "integer", "maximum", ToolLimits.SEARCH_MAX),
                    "category_id", Map.of("type", "integer"),
                    "account_id", Map.of("type", "integer"),
                    "card_id", Map.of("type", "integer"),
                    "type", Map.of("type", "string")
                ), "required", List.of()),
                Map.of("type", "object", "properties", Map.of("transacoes", Map.of("type", "array"))),
                CapabilityLatencyManifest.TRANSACTIONS_SEARCH
            );
            case "finance.invoice.read" -> cap(
                id,
                "Lê totais de uma fatura do usuário autenticado (sem dump de lançamentos).",
                List.of("ver fatura", "detalhe da fatura", "abrir fatura", "fatura do cartao", "mostrar fatura"),
                Map.of("type", "object", "properties", Map.of("invoice_id", Map.of("type", "integer")), "required", List.of("invoice_id")),
                Map.of("type", "object", "properties", Map.of("id", Map.of("type", "integer"), "valor_total", Map.of("type", "number"), "item_count", Map.of("type", "integer"))),
                CapabilityLatencyManifest.INVOICE_READ
            );
            case "finance.cards.list" -> cap(
                id,
                "Lista os cartões de crédito ativos do usuário com limite e dia de vencimento.",
                List.of("listar meus cartões", "quais cartões eu tenho", "meus cartoes", "mostrar cartões", "cartoes de credito", "lista cartao"),
                Map.of("type", "object", "properties", Map.of("limit", Map.of("type", "integer")), "required", List.of()),
                Map.of("type", "object", "properties", Map.of("cartoes", Map.of("type", "array"))),
                CapabilityLatencyManifest.CARDS_LIST
            );
            case "finance.month.summary" -> cap(
                id,
                "Resumo determinístico do mês: receitas, despesas, fluxo e saldo projetado. Sem texto de IA.",
                List.of("como vou fechar o mês", "resumo do mes", "fechar o mes", "como fecho o mes", "balanco do mes", "fechamento mensal"),
                Map.of("type", "object", "properties", Map.of("year_month", Map.of("type", "string")), "required", List.of()),
                Map.of("type", "object", "properties", Map.of("total_receitas", Map.of("type", "number"), "total_despesas", Map.of("type", "number"))),
                CapabilityLatencyManifest.MONTH_SUMMARY
            );
            case "finance.subscriptions.list" -> cap(
                id,
                "Lista assinaturas recorrentes ativas do usuário.",
                List.of("minhas assinaturas", "assinaturas recorrentes", "netflix e spotify", "listar assinaturas", "recorrentes de cartao"),
                Map.of("type", "object", "properties", Map.of("limit", Map.of("type", "integer")), "required", List.of()),
                Map.of("type", "object", "properties", Map.of("assinaturas", Map.of("type", "array"))),
                CapabilityLatencyManifest.SUBSCRIPTIONS_LIST
            );
            case "finance.recurring.list" -> cap(
                id,
                "Lista agendamentos de pagamento recorrentes.",
                List.of("agendamentos", "contas agendadas", "pagamentos recorrentes", "o que vence", "despesas agendadas"),
                Map.of("type", "object", "properties", Map.of("limit", Map.of("type", "integer")), "required", List.of()),
                Map.of("type", "object", "properties", Map.of("agendamentos", Map.of("type", "array"))),
                CapabilityLatencyManifest.RECURRING_LIST
            );
            case "finance.category.summary" -> cap(
                id,
                "Agrega despesas do mês por categoria no SQL (GROUP BY). Totais, não dump de linhas.",
                List.of("gastos por categoria", "resumo por categoria", "onde estou gastando", "categorias do mes", "despesas por tipo"),
                Map.of("type", "object", "properties", Map.of(
                    "year_month", Map.of("type", "string"),
                    "limit", Map.of("type", "integer", "maximum", ToolLimits.CATEGORY_MAX)
                ), "required", List.of()),
                Map.of("type", "object", "properties", Map.of("categorias", Map.of("type", "array"))),
                CapabilityLatencyManifest.CATEGORY_SUMMARY
            );
            case "finance.cashflow.project" -> cap(
                id,
                "Projeção determinística de caixa (forecast interno, sem LLM).",
                List.of("projeção de caixa", "projecao de caixa", "vou ficar no vermelho", "saldo previsto", "previsao financeira"),
                Map.of("type", "object", "properties", Map.of(), "required", List.of()),
                Map.of("type", "object", "properties", Map.of("saldo_previsto", Map.of("type", "number"))),
                CapabilityLatencyManifest.CASHFLOW_PROJECT
            );
            default -> null;
        };
    }

    private static Map<String, Object> cap(
        String id,
        String description,
        List<String> aliases,
        Map<String, Object> inputSchema,
        Map<String, Object> outputSchema,
        int p95
    ) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("description", description);
        m.put("aliases", aliases);
        m.put("input_schema", inputSchema);
        m.put("output_schema", outputSchema);
        m.put("execution", "DETERMINISTIC");
        m.put("kind", "READ");
        m.put("scope", "finance:read");
        m.put("idempotent", true);
        m.put("sensitivity", "FINANCIAL");
        m.put("p95_latency_ms", p95);
        m.put("cache_ttl_s", 60);
        m.put("requires_confirmation", false);
        m.put("local_resolvable", true);
        return m;
    }
}
