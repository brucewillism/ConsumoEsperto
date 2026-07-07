package com.consumoesperto.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "consumoesperto.jarvis.performance")
public class JarvisPerformanceProperties {

    /** TTL do cache de contexto financeiro por usuário (segundos). */
    private int contextoCacheTtlSeconds = 90;

    /** TTL do cache de indicadores de mercado (segundos). */
    private int marketCacheTtlSeconds = 7200;

    /** TTL da janela multi-turno por usuário (segundos). */
    private int conversaJanelaTtlSeconds = 3600;

    /** Máximo de trocas (user+assistant) na janela. */
    private int conversaJanelaMaxTrocas = 6;

    /** Ack textual após N ms de processamento (0 = desligado). */
    private long ackLongOperationMs = 3000;

    /** Timeout de leitura HTTP só para parse de comando (ms). */
    private int parseReadTimeoutMs = 25000;

    /** Amostras mantidas por etapa para p50/p95. */
    private int metricsSampleSize = 500;
}
