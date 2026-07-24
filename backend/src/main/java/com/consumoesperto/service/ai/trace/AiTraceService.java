package com.consumoesperto.service.ai.trace;

import com.consumoesperto.config.AiRouterProperties;
import com.consumoesperto.model.ai.AiTrace;
import com.consumoesperto.model.ai.AiTraceStatus;
import com.consumoesperto.service.ai.AITaskType;
import com.consumoesperto.service.ai.AiRouterMetrics;
import com.consumoesperto.service.ai.analytics.AiRouterAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiTraceService {

    private final AiTraceStore store;
    private final AiRouterProperties properties;
    private final AiRouterAlertService alertService;

    @PostConstruct
    void initStoreLimit() {
        store.setMaxSize(properties.getTraceMaxEntries());
    }

    public AiTrace.Mutable begin(
        Long userId,
        AITaskType taskType,
        String modeloPreferencial,
        Double temperatura
    ) {
        String traceId = UUID.randomUUID().toString();
        AiTrace.Mutable mutable = AiTrace.Mutable.start(traceId, userId, taskType, modeloPreferencial, temperatura);
        AiTraceHolder.set(mutable);
        return mutable;
    }

    public void finalizeSuccess(
        AiTrace.Mutable mutable,
        String modelo,
        long duracaoMs,
        int tokensEntrada,
        int tokensSaida,
        double custoUsd,
        boolean fallback
    ) {
        if (mutable == null) {
            return;
        }
        mutable.markSuccess(modelo, duracaoMs, tokensEntrada, tokensSaida, custoUsd, fallback);
        persistAndLog(mutable);
    }

    public void finalizeFailure(AiTrace.Mutable mutable, String erro) {
        if (mutable == null) {
            return;
        }
        mutable.markFailed(erro);
        persistAndLog(mutable);
    }

    public void attachStructuredOutput(Boolean valido, Boolean corrigido) {
        AiTraceHolder.current().ifPresent(m -> m.applyStructuredOutput(valido, corrigido));
        AiTraceHolder.lastCompletedTraceId().ifPresent(id -> store.patchStructuredOutput(id, valido, corrigido));
    }

    private void persistAndLog(AiTrace.Mutable mutable) {
        try {
            AiTrace frozen = mutable.freeze();
            store.add(frozen);
            AiTraceHolder.markCompleted(frozen.getTraceId());
            logStructuredTrace(frozen);
            alertService.evaluateAfterTrace(frozen);
        } finally {
            AiTraceHolder.clear();
        }
    }

    private void logStructuredTrace(AiTrace t) {
        String structLabel = structuredLabel(t);
        if (t.getStatus() == AiTraceStatus.SUCCESS) {
            log.info(
                "[AI_TRACE] traceId={} task={} modelo={} preferencial={} tempoMs={} fallback={} tentativas={} "
                    + "tokensIn={} tokensOut={} structuredOutput={} custoUsd={} userId={}",
                t.getTraceId(),
                t.getTaskType().name(),
                nullToDash(t.getModeloEscolhido()),
                nullToDash(t.getModeloPreferencial()),
                t.getDuracaoMs(),
                t.isFallbackUtilizado() ? "Sim" : "Não",
                t.getTentativas(),
                t.getTokensEntrada(),
                t.getTokensSaida(),
                structLabel,
                String.format(Locale.US, "%.5f", t.getCustoEstimadoUsd()),
                t.getUserId()
            );
            if (t.isFallbackUtilizado() && t.getTentativasModelos() != null && t.getTentativasModelos().size() > 1) {
                log.info(
                    "[AI_TRACE] traceId={} cadeiaFallback={} modeloFinal={} structuredOutput={}",
                    t.getTraceId(),
                    String.join(" → ", t.getTentativasModelos()),
                    t.getModeloEscolhido(),
                    structLabel
                );
            }
        } else {
            log.warn(
                "[AI_TRACE] traceId={} task={} status=FALHOU tentativas={} erro={} userId={}",
                t.getTraceId(),
                t.getTaskType().name(),
                t.getTentativas(),
                t.getErro(),
                t.getUserId()
            );
        }
    }

    private static String structuredLabel(AiTrace t) {
        if (t.getStructuredOutputValido() == null) {
            return "N/A";
        }
        if (Boolean.TRUE.equals(t.getStructuredOutputCorrigido())) {
            return "Corrigido";
        }
        return Boolean.TRUE.equals(t.getStructuredOutputValido()) ? "OK" : "Inválido";
    }

    private static String nullToDash(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }
}
