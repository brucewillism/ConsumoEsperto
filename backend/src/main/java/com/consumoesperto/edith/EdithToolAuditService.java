package com.consumoesperto.edith;

import com.consumoesperto.model.EdithToolAudit;
import com.consumoesperto.repository.EdithToolAuditRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Auditoria de invocação vinda da E.D.I.T.H. (Tool Bridge). Sem payload financeiro cru.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EdithToolAuditService {

    static final Set<String> PARAM_ALLOWLIST = Set.of(
        "limit", "date_from", "date_to", "invoice_id", "category_id",
        "account_id", "card_id", "year_month", "include_inactive", "type"
    );

    private final EdithToolAuditRepository repository;
    private final ObjectMapper objectMapper;
    private final EdithIntegrationService integrationService;

    @Value("${consumoesperto.edith.tool-audit.retention-days:90}")
    private int retentionDays;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
        String traceId,
        String toolCallId,
        String contextRef,
        String capability,
        Map<String, Object> arguments,
        Map<String, Object> result,
        long durationMs,
        String outcome
    ) {
        try {
            Long userId = contextRef != null
                ? integrationService.resolveUsuarioByContextRef(contextRef).orElse(null)
                : null;
            EdithToolAudit row = new EdithToolAudit(
                traceId != null ? traceId : "",
                toolCallId,
                userId,
                capability != null ? capability : "",
                toParamsJson(arguments),
                rowCount(result),
                durationMs,
                outcome != null ? outcome : "FAILED"
            );
            repository.save(row);
        } catch (Exception e) {
            log.warn("edith_tool_audit_failed error={}", e.getClass().getSimpleName());
        }
    }

    @Scheduled(cron = "0 20 4 * * ?", zone = "America/Sao_Paulo")
    @Transactional
    public void purgeExpired() {
        int days = Math.max(30, retentionDays);
        int deleted = repository.deleteCreatedBefore(LocalDateTime.now().minusDays(days));
        if (deleted > 0) {
            log.info("edith_tool_audit_purged rows={} retention_days={}", deleted, days);
        }
    }

    String toParamsJson(Map<String, Object> arguments) {
        Map<String, Object> safe = new LinkedHashMap<>();
        if (arguments != null) {
            for (Map.Entry<String, Object> e : arguments.entrySet()) {
                if (e.getKey() == null || !PARAM_ALLOWLIST.contains(e.getKey())) {
                    continue;
                }
                Object v = e.getValue();
                if (v instanceof Number || v instanceof Boolean) {
                    safe.put(e.getKey(), v);
                } else if (v != null) {
                    String s = String.valueOf(v);
                    safe.put(e.getKey(), s.length() > 64 ? s.substring(0, 64) : s);
                }
            }
        }
        try {
            return objectMapper.writeValueAsString(safe);
        } catch (Exception e) {
            return "{}";
        }
    }

    static int rowCount(Map<String, Object> result) {
        if (result == null) {
            return 0;
        }
        Object total = result.get("total");
        if (total instanceof Number n) {
            return n.intValue();
        }
        for (String key : new String[] {
            "transacoes", "contas", "cartoes", "assinaturas", "agendamentos", "categorias"
        }) {
            Object v = result.get(key);
            if (v instanceof Collection<?> c) {
                return c.size();
            }
        }
        if (result.containsKey("id") || result.containsKey("valor_total")
            || result.containsKey("saldo_previsto") || result.containsKey("total_despesas")) {
            return 1;
        }
        return 0;
    }
}
