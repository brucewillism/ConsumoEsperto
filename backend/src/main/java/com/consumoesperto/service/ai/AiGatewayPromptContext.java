package com.consumoesperto.service.ai;

/**
 * Metadados para {@link AiStrategySelector#selectStrategy(AiGatewayPromptContext)} (modo AUTO).
 */
public record AiGatewayPromptContext(
    String systemPrompt,
    String userPrompt,
    int estimatedTokens,
    boolean containsCode,
    int historyMessageCount,
    double complexityScore,
    boolean documentImport,
    boolean jsonOutput,
    String targetModelHint
) {
    public static Builder builder() {
        return new Builder();
    }

    public static AiGatewayPromptContext fromPrompts(String systemPrompt, String userPrompt) {
        return fromPrompts(systemPrompt, userPrompt, false, false);
    }

    public static AiGatewayPromptContext fromPrompts(
        String systemPrompt, String userPrompt, boolean documentImport, boolean jsonOutput
    ) {
        String sys = systemPrompt != null ? systemPrompt : "";
        String usr = userPrompt != null ? userPrompt : "";
        int chars = sys.length() + usr.length();
        int tokens = Math.max(1, chars / 4);
        return new AiGatewayPromptContext(
            sys,
            usr,
            tokens,
            detectCode(sys + "\n" + usr),
            0,
            estimateComplexity(sys, usr, documentImport, jsonOutput, tokens),
            documentImport,
            jsonOutput,
            null
        );
    }

    private static boolean detectCode(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String t = text.toLowerCase();
        return t.contains("```")
            || t.contains("public class ")
            || t.contains("def ")
            || t.contains("function ")
            || t.contains("import ")
            || t.contains("{\"action\"")
            || t.contains("json estrito")
            || t.contains("retorne apenas json");
    }

    private static double estimateComplexity(
        String sys, String usr, boolean documentImport, boolean jsonOutput, int tokens
    ) {
        double score = 0.25;
        if (tokens > 2_000) {
            score += 0.2;
        }
        if (tokens > 4_000) {
            score += 0.15;
        }
        if (jsonOutput) {
            score += 0.15;
        }
        if (documentImport) {
            score += 0.2;
        }
        String blob = (sys + usr).toLowerCase();
        long ruleLines = blob.lines().filter(l -> l.trim().startsWith("-")).count();
        if (ruleLines > 8) {
            score += 0.15;
        }
        if (blob.contains("regras:") || blob.contains("campos:")) {
            score += 0.1;
        }
        return Math.min(1.0, score);
    }

    public static final class Builder {
        private String systemPrompt = "";
        private String userPrompt = "";
        private int estimatedTokens = -1;
        private boolean containsCode;
        private int historyMessageCount;
        private double complexityScore = -1;
        private boolean documentImport;
        private boolean jsonOutput;
        private String targetModelHint;

        public Builder systemPrompt(String v) {
            this.systemPrompt = v != null ? v : "";
            return this;
        }

        public Builder userPrompt(String v) {
            this.userPrompt = v != null ? v : "";
            return this;
        }

        public Builder estimatedTokens(int v) {
            this.estimatedTokens = v;
            return this;
        }

        public Builder containsCode(boolean v) {
            this.containsCode = v;
            return this;
        }

        public Builder historyMessageCount(int v) {
            this.historyMessageCount = Math.max(0, v);
            return this;
        }

        public Builder complexityScore(double v) {
            this.complexityScore = v;
            return this;
        }

        public Builder documentImport(boolean v) {
            this.documentImport = v;
            return this;
        }

        public Builder jsonOutput(boolean v) {
            this.jsonOutput = v;
            return this;
        }

        public Builder targetModelHint(String v) {
            this.targetModelHint = v;
            return this;
        }

        public AiGatewayPromptContext build() {
            String sys = systemPrompt;
            String usr = userPrompt;
            int tokens = estimatedTokens > 0
                ? estimatedTokens
                : Math.max(1, (sys.length() + usr.length()) / 4);
            boolean code = containsCode || detectCode(sys + "\n" + usr);
            double complexity = complexityScore >= 0
                ? complexityScore
                : estimateComplexity(sys, usr, documentImport, jsonOutput, tokens);
            return new AiGatewayPromptContext(
                sys, usr, tokens, code, historyMessageCount, complexity,
                documentImport, jsonOutput, targetModelHint
            );
        }
    }
}
