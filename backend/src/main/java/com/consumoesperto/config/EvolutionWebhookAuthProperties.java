package com.consumoesperto.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Autenticação do webhook Evolution — fail-closed por padrão.
 * <p>
 * A Evolution v2 <strong>não</strong> envia {@code apikey} nos webhooks globais ({@code WEBHOOK_GLOBAL_URL}).
 * Use token na query da URL (Compose) ou header customizado via {@code POST /webhook/set/{instance}}.
 */
@Data
@Component
@ConfigurationProperties(prefix = "evolution.webhook")
public class EvolutionWebhookAuthProperties {

    /** Exige credencial válida (header ou query). Rollback documentado: {@code false}. */
    private boolean authRequired = true;

    /**
     * Segredo dedicado ao webhook. Se vazio, usa {@code evolution.apikey}.
     * Com {@code auth-required=true} e segredo vazio → 503 (fail-closed).
     */
    private String secret = "";

    /** Header HTTP enviado pela Evolution (configurável por instância). */
    private String headerName = "X-ConsumoEsperto-Webhook-Secret";

    /** Query param na URL do webhook (funciona com WEBHOOK_GLOBAL_URL). */
    private String queryParam = "token";

    /** Compatibilidade: também aceita header {@code apikey} da Evolution REST. */
    private boolean acceptLegacyApikeyHeader = true;
}
