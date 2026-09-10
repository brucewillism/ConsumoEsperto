package com.consumoesperto.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Monitor de sessão WhatsApp (Evolution): poll, backoff de reconexão e limiares de alerta.
 */
@Data
@Component
@ConfigurationProperties(prefix = "consumoesperto.whatsapp.monitor")
public class WhatsappConexaoMonitorProperties {

    /** Liga o job de sondagem e a reconexão automática (sem apagar instância). */
    private boolean enabled = true;

    /**
     * Cron Spring (6 campos) no fuso {@code America/Sao_Paulo}. Default: a cada 5 minutos.
     */
    private String cron = "0 */5 * * * *";

    /** Teto de tentativas automáticas após uma queda (depois só manual ou novo evento). */
    private int maxTentativas = 6;

    /** Dispara alerta operacional após este número de falhas de reconexão (não na 1.ª queda). */
    private int alertaAposTentativas = 5;

    /** Ou após a instância ficar fora deste número de minutos. */
    private int alertaAposMinutos = 15;

    /**
     * Minutos de espera após a 1.ª, 2.ª, … falha (ex.: 1,2,5,15,30).
     * Tentativa inicial é imediata.
     */
    private int[] backoffMinutos = {1, 2, 5, 15, 30};

    /** Validade anunciada do pairing code (alinhada a {@code QRCODE_LIMIT} da Evolution). */
    private int pairingCodeTtlSegundos = 90;
}
