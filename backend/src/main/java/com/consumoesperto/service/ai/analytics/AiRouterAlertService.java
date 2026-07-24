package com.consumoesperto.service.ai.analytics;

import com.consumoesperto.config.AiRouterProperties;
import com.consumoesperto.model.ai.AiTrace;
import com.consumoesperto.model.ai.AiTraceStatus;
import com.consumoesperto.service.AlertaOperacionalService;
import com.consumoesperto.util.AppTimeZone;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiRouterAlertService {

    public static final String TIPO_AI_FALLBACK_ALTO = "AI_FALLBACK_ALTO";
    public static final String TIPO_AI_LATENCIA_ALTA = "AI_LATENCIA_ALTA";
    public static final String TIPO_AI_MODELO_INDISPONIVEL = "AI_MODELO_INDISPONIVEL";
    public static final String TIPO_AI_STRUCTURED_INVALIDO = "AI_STRUCTURED_INVALIDO";
    public static final String TIPO_AI_CUSTO_DIARIO = "AI_CUSTO_DIARIO";

    private final AiRouterProperties properties;
    private final AiPerformanceAnalyticsService analytics;
    private final AlertaOperacionalService alertaOperacionalService;

    private final Deque<OperationalEvent> eventos = new ArrayDeque<>();
    private final Map<String, Instant> ultimoAlertaPorTipo = new ConcurrentHashMap<>();
    private final AtomicReference<LocalDate> custoDiaRef = new AtomicReference<>();
    private volatile double custoAcumuladoDia;

    public void evaluateAfterTrace(AiTrace trace) {
        if (trace == null) {
            return;
        }
        acumularCustoDiario(trace);
        List<AiTrace> janela = analytics.tracesBetween(
            Instant.now().minus(1, ChronoUnit.HOURS),
            Instant.now()
        );
        if (janela.size() < properties.getAlerts().getMinSamples()) {
            return;
        }
        checkFallbackRate(janela);
        checkLatencyIncrease(janela);
        checkStructuredInvalid(janela);
        checkDailyCost();
        if (trace.getStatus() == AiTraceStatus.FAILED && trace.getTentativas() > 0) {
            checkModelUnavailable(trace);
        }
    }

    public List<Map<String, Object>> listEvents(int limit) {
        List<Map<String, Object>> out = new ArrayList<>();
        synchronized (eventos) {
            int i = 0;
            for (OperationalEvent e : eventos) {
                out.add(e.toMap());
                if (++i >= Math.max(1, limit)) {
                    break;
                }
            }
        }
        return out;
    }

    private void acumularCustoDiario(AiTrace trace) {
        LocalDate hoje = AppTimeZone.hoje();
        LocalDate ref = custoDiaRef.get();
        if (ref == null || !ref.equals(hoje)) {
            custoDiaRef.set(hoje);
            custoAcumuladoDia = 0;
        }
        if (trace.getStatus() == AiTraceStatus.SUCCESS) {
            custoAcumuladoDia += trace.getCustoEstimadoUsd();
        }
    }

    private void checkFallbackRate(List<AiTrace> janela) {
        long fb = janela.stream().filter(AiTrace::isFallbackUtilizado).count();
        double rate = fb / (double) janela.size();
        if (rate > properties.getAlerts().getFallbackRateMax()) {
            registrar(
                TIPO_AI_FALLBACK_ALTO,
                String.format(
                    Locale.forLanguageTag("pt-PT"),
                    "Taxa de fallback de IA atingiu %.0f%% na última hora (limite %.0f%%).",
                    rate * 100,
                    properties.getAlerts().getFallbackRateMax() * 100
                )
            );
        }
    }

    private void checkLatencyIncrease(List<AiTrace> janela) {
        List<AiTrace> prev = analytics.tracesBetween(
            Instant.now().minus(2, ChronoUnit.HOURS),
            Instant.now().minus(1, ChronoUnit.HOURS)
        );
        if (prev.isEmpty()) {
            return;
        }
        double avgNow = janela.stream().mapToLong(AiTrace::getDuracaoMs).average().orElse(0);
        double avgPrev = prev.stream().mapToLong(AiTrace::getDuracaoMs).average().orElse(0);
        if (avgPrev > 0 && avgNow > avgPrev * (1.0 + properties.getAlerts().getLatencyIncreaseMax())) {
            registrar(
                TIPO_AI_LATENCIA_ALTA,
                String.format(
                    Locale.forLanguageTag("pt-PT"),
                    "Tempo médio de IA subiu %.0f%% (de %.0f ms para %.0f ms).",
                    ((avgNow / avgPrev) - 1) * 100,
                    avgPrev,
                    avgNow
                )
            );
        }
    }

    private void checkStructuredInvalid(List<AiTrace> janela) {
        long withStruct = janela.stream().filter(t -> t.getStructuredOutputValido() != null).count();
        if (withStruct < 5) {
            return;
        }
        long invalid = janela.stream()
            .filter(t -> Boolean.FALSE.equals(t.getStructuredOutputValido()))
            .count();
        double rate = invalid / (double) withStruct;
        if (rate > properties.getAlerts().getStructuredInvalidMax()) {
            registrar(
                TIPO_AI_STRUCTURED_INVALIDO,
                String.format(
                    Locale.forLanguageTag("pt-PT"),
                    "Structured Outputs inválidos atingiram %.0f%% (limite %.0f%%).",
                    rate * 100,
                    properties.getAlerts().getStructuredInvalidMax() * 100
                )
            );
        }
    }

    private void checkDailyCost() {
        if (custoAcumuladoDia > properties.getAlerts().getDailyCostLimitUsd()) {
            registrar(
                TIPO_AI_CUSTO_DIARIO,
                String.format(
                    Locale.forLanguageTag("pt-PT"),
                    "Custo diário estimado de IA (US$ %.2f) ultrapassou o limite (US$ %.2f).",
                    custoAcumuladoDia,
                    properties.getAlerts().getDailyCostLimitUsd()
                )
            );
        }
    }

    private void checkModelUnavailable(AiTrace trace) {
        registrar(
            TIPO_AI_MODELO_INDISPONIVEL,
            String.format(
                Locale.forLanguageTag("pt-PT"),
                "Falha total na cadeia %s após %d tentativa(s). Último erro: %s",
                trace.getTaskType().name(),
                trace.getTentativas(),
                trace.getErro() != null ? trace.getErro() : "desconhecido"
            )
        );
    }

    private void registrar(String tipo, String mensagem) {
        if (emCooldown(tipo)) {
            return;
        }
        OperationalEvent ev = new OperationalEvent(Instant.now(), tipo, mensagem);
        synchronized (eventos) {
            eventos.addFirst(ev);
            while (eventos.size() > 200) {
                eventos.removeLast();
            }
        }
        log.error("[ALERTA-OP-AI] tipo={} mensagem={}", tipo, mensagem);
        alertaOperacionalService.alertar(tipo, mensagem);
    }

    private boolean emCooldown(String tipo) {
        Instant agora = Instant.now();
        Instant ultimo = ultimoAlertaPorTipo.get(tipo);
        int min = Math.max(5, properties.getAlerts().getCooldownMinutes());
        if (ultimo != null && ultimo.plus(min, ChronoUnit.MINUTES).isAfter(agora)) {
            return true;
        }
        ultimoAlertaPorTipo.put(tipo, agora);
        return false;
    }

    @Getter
    private static final class OperationalEvent {
        private final Instant at;
        private final String tipo;
        private final String mensagem;

        OperationalEvent(Instant at, String tipo, String mensagem) {
            this.at = at;
            this.tipo = tipo;
            this.mensagem = mensagem;
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("at", at.toString());
            m.put("tipo", tipo);
            m.put("mensagem", mensagem);
            return m;
        }
    }
}
