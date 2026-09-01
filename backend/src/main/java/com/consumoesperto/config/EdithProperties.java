package com.consumoesperto.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuração central da integração E.D.I.T.H. (Enhanced Distributed Intelligence &amp; Task Hub).
 */
@Data
@Component
@ConfigurationProperties(prefix = "consumoesperto.edith")
public class EdithProperties {

    /** Feature flag — false por padrão; app inicia normalmente desligada. */
    private boolean enabled = false;

    private String baseUrl = "";

    private String apiKey = "";

    private String callbackSecret = "";

    /** Identificador da aplicação no hub E.D.I.T.H. */
    private String application = "CONSUMO_ESPERTO";

    /** Projeto/tenant lógico dentro da aplicação. */
    private String project = "CONSUMO_ESPERTO";

    /** Timeout HTTP para requests à API E.D.I.T.H. (ms). */
    private long requestTimeoutMs = 30_000L;

    /** Timeout máximo de acompanhamento de Task (ms). */
    private long taskTimeoutMs = 300_000L;

    /** Intervalo de polling de eventos da Task quando SSE externo indisponível (ms). */
    private long taskPollIntervalMs = 1_500L;

    /** Janela máxima de aceitação de timestamp no callback HMAC (segundos). */
    private long callbackTimestampSkewSeconds = 300L;

    /** Tamanho máximo do body do callback de tools (bytes). */
    private int callbackMaxBodyBytes = 65_536;

    /** Limite de requisições de callback por minuto (por IP). */
    private int callbackRateLimitPerMinute = 120;

    /** URL pública do callback (informativa; usada em metadata para E.D.I.T.H.). */
    private String callbackUrl = "";

    private Sse sse = new Sse();

    @Data
    public static class Sse {
        /** Timeout do SseEmitter para o frontend (ms). */
        private long emitterTimeoutMs = 300_000L;
    }
}
