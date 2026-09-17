package com.consumoesperto.controller;

import com.consumoesperto.autonomy.AutonomyForecastService;
import com.consumoesperto.autonomy.AutonomyPreferenciaService;
import com.consumoesperto.autonomy.AutonomyReviewService;
import com.consumoesperto.autonomy.LiveInvoiceProjectionService;
import com.consumoesperto.autonomy.SafeToSpendService;
import com.consumoesperto.config.FinancialAutonomyProperties;
import com.consumoesperto.model.AutonomyPreferencia;
import com.consumoesperto.model.AutonomyReviewItem;
import com.consumoesperto.repository.AutonomyDecisionLogRepository;
import com.consumoesperto.repository.TransactionEvidenceRepository;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.util.AppTimeZone;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/autonomy")
@RequiredArgsConstructor
public class AutonomyController {

    private final FinancialAutonomyProperties properties;
    private final AutonomyPreferenciaService preferenciaService;
    private final AutonomyReviewService reviewService;
    private final SafeToSpendService safeToSpendService;
    private final AutonomyForecastService forecastService;
    private final LiveInvoiceProjectionService liveInvoiceProjectionService;
    private final AutonomyDecisionLogRepository decisionLogRepository;
    private final TransactionEvidenceRepository evidenceRepository;

    @GetMapping("/resumo")
    public ResponseEntity<Map<String, Object>> resumo(@AuthenticationPrincipal UserPrincipal user) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", properties.isEnabled());
        if (!properties.isEnabled()) {
            return ResponseEntity.ok(body);
        }
        Long usuarioId = user.getId();
        LocalDateTime inicioHoje = AppTimeZone.hoje().atStartOfDay();
        body.put("revisoesPendentes", reviewService.countOpen(usuarioId));
        body.put("transacoesAutoHoje", decisionLogRepository
            .countByUsuarioIdAndCreatedAtGreaterThanEqualAndResult(usuarioId, inicioHoje, "EXECUTED"));
        body.put("itensConciliadosHoje", evidenceRepository
            .countByUsuarioIdAndFirstSeenAtGreaterThanEqual(usuarioId, inicioHoje));
        body.put("anomaliasAbertas", reviewService.listOpen(usuarioId).stream()
            .filter(i -> "ANOMALY".equals(i.getKind()) || "DUPLICATE".equals(i.getKind()))
            .count());
        try {
            body.put("safeToSpend", safeToSpendService.calcular(usuarioId));
        } catch (Exception e) {
            body.put("safeToSpend", Map.of());
        }
        try {
            body.put("forecast", forecastService.horizontes(usuarioId));
        } catch (Exception e) {
            body.put("forecast", Map.of());
        }
        try {
            body.put("faturas", liveInvoiceProjectionService.atuais(usuarioId));
        } catch (Exception e) {
            body.put("faturas", java.util.List.of());
        }
        return ResponseEntity.ok(body);
    }

    @GetMapping("/revisao")
    public ResponseEntity<Map<String, Object>> revisao(@AuthenticationPrincipal UserPrincipal user) {
        var itens = reviewService.listOpen(user.getId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", itens.size());
        body.put("itens", itens.stream().map(this::toReviewMap).toList());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/revisao/{id}/resolver")
    public ResponseEntity<Map<String, String>> resolver(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable Long id
    ) {
        reviewService.resolve(user.getId(), id);
        return ResponseEntity.ok(Map.of("status", "RESOLVED"));
    }

    @GetMapping("/preferencias")
    public ResponseEntity<Map<String, Object>> preferencias(@AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(toPrefMap(preferenciaService.obterOuCriar(user.getId())));
    }

    @PutMapping("/preferencias")
    public ResponseEntity<Map<String, Object>> salvarPreferencias(
        @AuthenticationPrincipal UserPrincipal user,
        @RequestBody Map<String, Object> body
    ) {
        AutonomyPreferencia incoming = preferenciaService.obterOuCriar(user.getId());
        if (body.get("nivel") != null) {
            incoming.setNivel(String.valueOf(body.get("nivel")));
        }
        if (body.get("registrarAuto") != null) {
            incoming.setRegistrarAuto(asBoolean(body.get("registrarAuto")));
        }
        if (body.get("classificarAuto") != null) {
            incoming.setClassificarAuto(asBoolean(body.get("classificarAuto")));
        }
        if (body.get("aprenderCategorias") != null) {
            incoming.setAprenderCategorias(asBoolean(body.get("aprenderCategorias")));
        }
        if (body.get("detectarAssinaturas") != null) {
            incoming.setDetectarAssinaturas(asBoolean(body.get("detectarAssinaturas")));
        }
        if (body.get("detectarDuplicatas") != null) {
            incoming.setDetectarDuplicatas(asBoolean(body.get("detectarDuplicatas")));
        }
        if (body.get("detectarAnomalias") != null) {
            incoming.setDetectarAnomalias(asBoolean(body.get("detectarAnomalias")));
        }
        if (body.get("preverSaldo") != null) {
            incoming.setPreverSaldo(asBoolean(body.get("preverSaldo")));
        }
        if (body.get("jarvisProativo") != null) {
            incoming.setJarvisProativo(asBoolean(body.get("jarvisProativo")));
        }
        if (body.get("resumoDiario") != null) {
            incoming.setResumoDiario(asBoolean(body.get("resumoDiario")));
        }
        if (body.get("resumoSemanal") != null) {
            incoming.setResumoSemanal(asBoolean(body.get("resumoSemanal")));
        }
        if (body.get("silenciosoInicio") != null && !String.valueOf(body.get("silenciosoInicio")).isBlank()) {
            incoming.setSilenciosoInicio(LocalTime.parse(String.valueOf(body.get("silenciosoInicio"))));
        }
        if (body.get("silenciosoFim") != null && !String.valueOf(body.get("silenciosoFim")).isBlank()) {
            incoming.setSilenciosoFim(LocalTime.parse(String.valueOf(body.get("silenciosoFim"))));
        }
        return ResponseEntity.ok(toPrefMap(preferenciaService.salvar(user.getId(), incoming)));
    }

    private Map<String, Object> toPrefMap(AutonomyPreferencia p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nivel", p.getNivel());
        m.put("registrarAuto", p.isRegistrarAuto());
        m.put("classificarAuto", p.isClassificarAuto());
        m.put("aprenderCategorias", p.isAprenderCategorias());
        m.put("detectarAssinaturas", p.isDetectarAssinaturas());
        m.put("detectarDuplicatas", p.isDetectarDuplicatas());
        m.put("detectarAnomalias", p.isDetectarAnomalias());
        m.put("preverSaldo", p.isPreverSaldo());
        m.put("jarvisProativo", p.isJarvisProativo());
        m.put("resumoDiario", p.isResumoDiario());
        m.put("resumoSemanal", p.isResumoSemanal());
        m.put("silenciosoInicio", p.getSilenciosoInicio());
        m.put("silenciosoFim", p.getSilenciosoFim());
        m.put("flags", Map.of(
            "FINANCIAL_AUTONOMY_ENABLED", properties.isEnabled(),
            "AUTONOMY_PROACTIVE_JARVIS_ENABLED", properties.isProactiveJarvis(),
            "AUTONOMY_EDITH_ENABLED", properties.isEdith(),
            "AUTONOMY_MERCHANT_LEARNING_ENABLED", properties.isMerchantLearning(),
            "AUTONOMY_RECONCILIATION_ENABLED", properties.isReconciliation(),
            "AUTONOMY_ANOMALY_ENABLED", properties.isAnomaly()
        ));
        return m;
    }

    private Map<String, Object> toReviewMap(AutonomyReviewItem item) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", item.getId());
        m.put("kind", item.getKind());
        m.put("transacaoId", item.getTransacaoId());
        m.put("title", item.getTitle());
        m.put("detail", item.getDetail());
        m.put("confidence", item.getConfidence());
        m.put("createdAt", item.getCreatedAt());
        return m;
    }

    private static boolean asBoolean(Object v) {
        if (v instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(v));
    }
}
