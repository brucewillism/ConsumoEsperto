package com.consumoesperto.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Alertas operacionais (divergência de saldo, falha de auth do webhook, WhatsApp).
 * Baseline = log ERROR estruturado; webhook e e-mail são opcionais e não bloqueiam o fluxo.
 */
@Data
@Component
@ConfigurationProperties(prefix = "consumoesperto.alertas")
public class AlertasOperacionaisProperties {

    /** Liga/desliga o envio para webhook externo (logs ERROR saem sempre). */
    private boolean webhookEnabled = false;

    /** URL que recebe POST JSON {tipo, mensagem, timestamp}. Vazio = só log. */
    private String webhookUrl = "";

    /** Minutos mínimos entre alertas do mesmo tipo (anti-spam de webhook e e-mail). */
    private int cooldownMinutes = 15;

    /** Timeout do POST do alerta (ms) — falha não pode travar o fluxo principal. */
    private int timeoutMs = 5000;

    /** Canal SMTP. Falha de envio nunca propaga. */
    private boolean emailEnabled = false;

    /** Destino dos alertas (caixa do operador). */
    private String emailDestino = "";

    /** Remetente (ex. conta Gmail com senha de app). */
    private String emailFrom = "";
}
