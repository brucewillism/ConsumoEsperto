package com.consumoesperto.service.ai.provider;

import com.consumoesperto.config.AiRouterProperties;
import com.consumoesperto.service.AiProviderType;
import com.consumoesperto.service.ai.AiRouterCapability;
import org.springframework.stereotype.Component;

/**
 * Adaptadores built-in mapeados a {@link AiProviderType} existente.
 * Novos provedores (Mistral, Qwen…) adicionam beans {@link AiProviderAdapter} separados.
 */
@Component
public class BuiltinAiProviderAdapters {

    @Component
    public static class GroqAdapter extends AbstractBuiltinAdapter {
        public GroqAdapter(AiRouterProperties props) {
            super(AiProviderType.GROQ, () -> props.getGroq().isEnabled(), new double[] {0.05, 0.08});
        }

        @Override
        public boolean supports(AiRouterCapability capability) {
            return capability != AiRouterCapability.VISION || true;
        }
    }

    @Component
    public static class OpenAiAdapter extends AbstractBuiltinAdapter {
        public OpenAiAdapter(AiRouterProperties props) {
            super(AiProviderType.OPENAI, () -> props.getOpenai().isEnabled(), new double[] {2.5, 10.0});
        }

        @Override
        public boolean supports(AiRouterCapability capability) {
            return true;
        }
    }

    @Component
    public static class ClaudeAdapter extends AbstractBuiltinAdapter {
        public ClaudeAdapter(AiRouterProperties props) {
            super(AiProviderType.CLAUDE, () -> props.getClaude().isEnabled(), new double[] {0.8, 4.0});
        }

        @Override
        public boolean supports(AiRouterCapability capability) {
            return capability != AiRouterCapability.TRANSCRIBE;
        }
    }

    @Component
    public static class GeminiAdapter extends AbstractBuiltinAdapter {
        public GeminiAdapter(AiRouterProperties props) {
            super(AiProviderType.GEMINI, () -> props.getGemini().isEnabled(), new double[] {0.1, 0.4});
        }

        @Override
        public boolean supports(AiRouterCapability capability) {
            return capability != AiRouterCapability.TRANSCRIBE;
        }
    }

    @Component
    public static class DeepSeekAdapter extends AbstractBuiltinAdapter {
        public DeepSeekAdapter(AiRouterProperties props) {
            super(AiProviderType.DEEPSEEK, () -> props.getDeepseek().isEnabled(), new double[] {0.14, 0.28});
        }

        @Override
        public boolean supports(AiRouterCapability capability) {
            return capability == AiRouterCapability.CHAT_JSON;
        }
    }

    @Component
    public static class OllamaAdapter extends AbstractBuiltinAdapter {
        public OllamaAdapter(AiRouterProperties props) {
            super(AiProviderType.OLLAMA, () -> props.getOllama().isEnabled(), new double[] {0.0, 0.0});
        }

        @Override
        public boolean supports(AiRouterCapability capability) {
            return true;
        }
    }

    abstract static class AbstractBuiltinAdapter implements AiProviderAdapter {
        private final AiProviderType type;
        private final java.util.function.BooleanSupplier enabled;
        private final double[] costPerM;

        AbstractBuiltinAdapter(AiProviderType type, java.util.function.BooleanSupplier enabled, double[] costPerM) {
            this.type = type;
            this.enabled = enabled;
            this.costPerM = costPerM;
        }

        @Override
        public String providerId() {
            return type.name();
        }

        @Override
        public AiProviderType providerType() {
            return type;
        }

        @Override
        public boolean isEnabled() {
            return enabled.getAsBoolean();
        }

        @Override
        public double[] costPerMillionTokens() {
            return costPerM;
        }
    }
}
