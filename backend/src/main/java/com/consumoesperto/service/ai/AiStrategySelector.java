package com.consumoesperto.service.ai;

import org.springframework.stereotype.Component;

/**
 * Modo AUTO: escolhe estratégia ATS sem usar {@code ultra} para todos os pedidos.
 */
@Component
public class AiStrategySelector {

    /**
     * Regras pedidas: fast para prompts curtos; code-focused com código; ultra só para
     * contexto grande ou histórico longo; balanced para alta complexidade semântica.
     */
    public AiGatewayStrategy selectStrategy(AiGatewayPromptContext ctx) {
        if (ctx == null) {
            return AiGatewayStrategy.FAST;
        }
        int tokens = ctx.estimatedTokens();
        if (tokens < 1_000) {
            return AiGatewayStrategy.FAST;
        }
        if (ctx.containsCode()) {
            return AiGatewayStrategy.CODE_FOCUSED;
        }
        if (tokens > 4_000) {
            return AiGatewayStrategy.ULTRA;
        }
        if (ctx.historyMessageCount() > 12) {
            return AiGatewayStrategy.ULTRA;
        }
        if (ctx.complexityScore() > 0.8) {
            return AiGatewayStrategy.BALANCED;
        }
        return AiGatewayStrategy.FAST;
    }
}
