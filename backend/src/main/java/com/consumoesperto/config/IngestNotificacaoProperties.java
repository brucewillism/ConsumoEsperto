package com.consumoesperto.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "consumoesperto.ingest.notificacao")
public class IngestNotificacaoProperties {

    private boolean enabled = false;
    /** Processar após o 202. Em testes, {@code false} para o mesmo pedido. */
    private boolean async = true;
    private boolean requireHttps = true;
    private int rateLimitPerMinute = 60;
    private int maxPayloadBytes = 8192;
    private int maxTextoChars = 1000;
    private int dedupWindowMinutes = 15;
    private int matchManualHours = 2;
    private int retentionDays = 90;
    private String ingestionBaseUrl = "http://localhost:18081";
    /** Horário silencioso padrão (America/Sao_Paulo) se o utilizador não configurar. */
    private String silenciosoInicioDefault = "22:00";
    private String silenciosoFimDefault = "07:00";
    /** Jobs de expurgo e flush de avisos. Desligar em testes (@EnableScheduling continua activo). */
    private boolean jobs = true;
}
