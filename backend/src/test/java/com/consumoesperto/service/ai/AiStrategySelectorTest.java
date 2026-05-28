package com.consumoesperto.service.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiStrategySelectorTest {

    private final AiStrategySelector selector = new AiStrategySelector();

    @Test
    void shortPromptUsesFast() {
        AiGatewayPromptContext ctx = AiGatewayPromptContext.builder()
            .systemPrompt("ok")
            .userPrompt("hi")
            .estimatedTokens(400)
            .build();
        assertEquals(AiGatewayStrategy.FAST, selector.selectStrategy(ctx));
    }

    @Test
    void codePromptUsesCodeFocused() {
        AiGatewayPromptContext ctx = AiGatewayPromptContext.builder()
            .systemPrompt("```java\npublic class X {}\n```")
            .userPrompt("fix")
            .estimatedTokens(1500)
            .containsCode(true)
            .build();
        assertEquals(AiGatewayStrategy.CODE_FOCUSED, selector.selectStrategy(ctx));
    }

    @Test
    void largeContextUsesUltra() {
        AiGatewayPromptContext ctx = AiGatewayPromptContext.builder()
            .systemPrompt("x".repeat(1000))
            .userPrompt("y".repeat(1000))
            .estimatedTokens(5000)
            .build();
        assertEquals(AiGatewayStrategy.ULTRA, selector.selectStrategy(ctx));
    }

    @Test
    void longHistoryUsesUltra() {
        AiGatewayPromptContext ctx = AiGatewayPromptContext.builder()
            .estimatedTokens(2000)
            .historyMessageCount(15)
            .build();
        assertEquals(AiGatewayStrategy.ULTRA, selector.selectStrategy(ctx));
    }

    @Test
    void highComplexityUsesBalanced() {
        AiGatewayPromptContext ctx = AiGatewayPromptContext.builder()
            .estimatedTokens(2500)
            .complexityScore(0.85)
            .build();
        assertEquals(AiGatewayStrategy.BALANCED, selector.selectStrategy(ctx));
    }
}
