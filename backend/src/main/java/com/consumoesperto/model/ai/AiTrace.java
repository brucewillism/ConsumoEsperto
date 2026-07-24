package com.consumoesperto.model.ai;

import com.consumoesperto.service.ai.AITaskType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Rastreamento completo de uma chamada de IA via roteador.
 */
@Getter
@Builder
public class AiTrace {

    private final String traceId;
    private final Long userId;
    private final AITaskType taskType;
    private final String modeloEscolhido;
    private final String modeloPreferencial;
    private final String modeloFallback;
    private final Instant inicioExecucao;
    private final Instant fimExecucao;
    private final long duracaoMs;
    private final int tokensEntrada;
    private final int tokensSaida;
    private final double custoEstimadoUsd;
    private final Double temperatura;
    private final int tentativas;
    private final boolean fallbackUtilizado;
    private final Boolean structuredOutputValido;
    private final Boolean structuredOutputCorrigido;
    private final AiTraceStatus status;
    private final String erro;
    @Builder.Default
    private final List<String> tentativasModelos = List.of();

    public static AiTraceBuilder builderFrom(AiTraceBuilder b) {
        return b;
    }

    /** Builder mutável usado durante execução do roteador. */
    public static class Mutable {
        private String traceId;
        private Long userId;
        private AITaskType taskType;
        private String modeloPreferencial;
        private String modeloEscolhido;
        private String modeloFallback;
        private Instant inicioExecucao = Instant.now();
        private Instant fimExecucao;
        private int tokensEntrada;
        private int tokensSaida;
        private double custoEstimadoUsd;
        private Double temperatura;
        private int tentativas;
        private boolean fallbackUtilizado;
        private Boolean structuredOutputValido;
        private Boolean structuredOutputCorrigido;
        private AiTraceStatus status;
        private String erro;
        private final List<String> tentativasModelos = new ArrayList<>();

        public static Mutable start(String traceId, Long userId, AITaskType taskType, String modeloPreferencial, Double temperatura) {
            Mutable m = new Mutable();
            m.traceId = traceId;
            m.userId = userId;
            m.taskType = taskType;
            m.modeloPreferencial = modeloPreferencial;
            m.temperatura = temperatura;
            return m;
        }

        public void addAttempt(String modelo, boolean falhou) {
            tentativasModelos.add(modelo + (falhou ? ":FALHOU" : ":OK"));
            tentativas = tentativasModelos.size();
        }

        public void markSuccess(String modelo, long duracaoMs, int inTok, int outTok, double custo, boolean fallback) {
            this.modeloEscolhido = modelo;
            this.fallbackUtilizado = fallback;
            if (fallback && !tentativasModelos.isEmpty()) {
                this.modeloFallback = modelo;
            }
            this.fimExecucao = Instant.now();
            this.duracaoMs = duracaoMs;
            this.tokensEntrada = inTok;
            this.tokensSaida = outTok;
            this.custoEstimadoUsd = custo;
            this.status = AiTraceStatus.SUCCESS;
            this.erro = null;
        }

        public void markFailed(String erroMsg) {
            this.fimExecucao = Instant.now();
            this.duracaoMs = Math.max(0, fimExecucao.toEpochMilli() - inicioExecucao.toEpochMilli());
            this.status = AiTraceStatus.FAILED;
            this.erro = erroMsg;
        }

        public void applyStructuredOutput(Boolean valido, Boolean corrigido) {
            this.structuredOutputValido = valido;
            this.structuredOutputCorrigido = corrigido;
        }

        private long duracaoMs;

        public AiTraceStatus getStatus() {
            return status;
        }

        public AiTrace freeze() {
            return AiTrace.builder()
                .traceId(traceId)
                .userId(userId)
                .taskType(taskType)
                .modeloEscolhido(modeloEscolhido)
                .modeloPreferencial(modeloPreferencial)
                .modeloFallback(modeloFallback)
                .inicioExecucao(inicioExecucao)
                .fimExecucao(fimExecucao)
                .duracaoMs(duracaoMs)
                .tokensEntrada(tokensEntrada)
                .tokensSaida(tokensSaida)
                .custoEstimadoUsd(custoEstimadoUsd)
                .temperatura(temperatura)
                .tentativas(tentativas)
                .fallbackUtilizado(fallbackUtilizado)
                .structuredOutputValido(structuredOutputValido)
                .structuredOutputCorrigido(structuredOutputCorrigido)
                .status(status)
                .erro(erro)
                .tentativasModelos(Collections.unmodifiableList(new ArrayList<>(tentativasModelos)))
                .build();
        }
    }
}
