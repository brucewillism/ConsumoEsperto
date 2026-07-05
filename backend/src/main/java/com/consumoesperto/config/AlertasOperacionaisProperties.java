package com.consumoesperto.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Alertas operacionais (divergência de saldo, falha de auth do webhook).
 * Baseline = log ERROR estruturado; webhook externo é opcional e não bloqueia o boot.
 */
@Data
@Component
@ConfigurationProperties(prefix = "consumoesperto.alertas")
public class AlertasOperacionaisProperties {

    /** Liga/desliga o envio para webhook externo (logs ERROR saem sempre). */
    private boolean webhookEnabled = false;

    /** URL que recebe POST JSON {tipo, mensagem, timestamp}. Vazio = só log. */
    private String webhookUrl = "";

    /** Minutos mínimos entre alertas do mesmo tipo (anti-spam). */
    private int cooldownMinutes = 15;

    /** Timeout do POST do alerta (ms) — falha não pode travar o fluxo principal. */
    private int timeoutMs = 5000;
}
