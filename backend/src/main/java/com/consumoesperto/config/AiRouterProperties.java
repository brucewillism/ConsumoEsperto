package com.consumoesperto.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "consumoesperto.ai.router")
public class AiRouterProperties {

    /** Habilita o roteamento inteligente por tipo de tarefa (fallback por categoria). */
    private boolean enabled = true;

    /** Inclui Ollama como fallback de emergência no fim da cadeia (dev/local). */
    private boolean ollamaEmergencyFallback = false;

    private ProviderFlags groq = new ProviderFlags(true);
    private ProviderFlags openai = new ProviderFlags(true);
    private ProviderFlags claude = new ProviderFlags(true);
    private ProviderFlags gemini = new ProviderFlags(true);
    private ProviderFlags deepseek = new ProviderFlags(true);
    private ProviderFlags ollama = new ProviderFlags(false);

    /** Máximo de traces em memória para auditoria admin. */
    private int traceMaxEntries = 10_000;

    private AlertThresholds alerts = new AlertThresholds();

    @Data
    public static class AlertThresholds {
        /** Taxa máxima de fallback (0-1) antes de alerta. */
        private double fallbackRateMax = 0.15;
        /** Aumento relativo de latência média (0-1 = 50%). */
        private double latencyIncreaseMax = 0.50;
        /** Taxa máxima de structured output inválido. */
        private double structuredInvalidMax = 0.05;
        /** Limite de custo diário estimado (USD). */
        private double dailyCostLimitUsd = 25.0;
        /** Amostras mínimas na janela antes de alertas. */
        private int minSamples = 10;
        /** Cooldown entre alertas do mesmo tipo (minutos). */
        private int cooldownMinutes = 30;
    }

    @Data
    public static class ProviderFlags {
        private boolean enabled;

        public ProviderFlags() {
        }

        public ProviderFlags(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
