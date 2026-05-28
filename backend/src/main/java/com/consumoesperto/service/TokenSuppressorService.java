package com.consumoesperto.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Envia prompts ao AI Token Suppressor ({@code POST /api/optimize}) antes das chamadas Groq/Gemini/OpenAI.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenSuppressorService {

    private final ObjectMapper objectMapper;

    @Value("${consumoesperto.ai.token-suppressor.enabled:false}")
    private boolean enabled;

    @Value("${consumoesperto.ai.token-suppressor.base-url:}")
    private String baseUrl;

    @Value("${consumoesperto.ai.token-suppressor.api-key:}")
    private String apiKey;

    @Value("${consumoesperto.ai.token-suppressor.strategy:balanced}")
    private String strategy;

    /** 0 = sempre otimiza quando enabled (suppressor antes de qualquer chamada de IA). */
    @Value("${consumoesperto.ai.token-suppressor.min-chars:0}")
    private int minChars;

    @Value("${consumoesperto.ai.token-suppressor.timeout-ms:120000}")
    private int timeoutMs;

    public boolean isEnabled() {
        return enabled && baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank();
    }

    /**
     * Otimiza system + user; em falha ou prompt curto devolve vazio (caller usa texto original).
     */
    public Optional<OptimizedPrompt> tryOptimize(Long userId, String systemPrompt, String userPrompt, String targetModel) {
        return tryOptimize(userId, systemPrompt, userPrompt, targetModel, null);
    }

    /**
     * @param strategyOverride estratégia ATS (fast, balanced, code-focused, ultra); null usa config global.
     */
    public Optional<OptimizedPrompt> tryOptimize(
        Long userId,
        String systemPrompt,
        String userPrompt,
        String targetModel,
        String strategyOverride
    ) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        String sys = systemPrompt != null ? systemPrompt : "";
        String usr = userPrompt != null ? userPrompt : "";
        int totalLen = sys.length() + usr.length();
        if (minChars > 0 && totalLen < minChars) {
            log.debug("[TokenSuppressor] prompt curto ({} chars < {}), skip", totalLen, minChars);
            return Optional.empty();
        }
        if (totalLen == 0) {
            return Optional.empty();
        }

        try {
            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode messages = body.putArray("messages");
            if (!sys.isBlank()) {
                ObjectNode s = messages.addObject();
                s.put("role", "system");
                s.put("content", sys);
            }
            ObjectNode u = messages.addObject();
            u.put("role", "user");
            u.put("content", usr.isBlank() ? "." : usr);

            String effectiveStrategy = strategyOverride != null && !strategyOverride.isBlank()
                ? strategyOverride.trim()
                : (strategy != null && !strategy.isBlank() ? strategy : "balanced");
            body.put("strategy", effectiveStrategy);
            body.put("user_id", userId != null ? "consumoesperto-" + userId : "consumoesperto");
            body.put("target_model", targetModel != null && !targetModel.isBlank() ? targetModel : "gemini-2.5-flash");
            body.put("use_memory", false);
            body.put("use_rag", false);
            body.put("use_hierarchical_memory", false);
            body.put("use_semantic_cache", true);
            body.put("check_semantic_loss", true);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-API-Key", apiKey.trim());

            String url = trimTrailingSlash(baseUrl) + "/api/optimize";
            RestTemplate rt = restTemplateWithTimeout();
            String jsonBody = objectMapper.writeValueAsString(body);
            ResponseEntity<String> response = rt.exchange(
                url, HttpMethod.POST, new HttpEntity<>(jsonBody, headers), String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("[TokenSuppressor] HTTP {}", response.getStatusCode());
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            ParsedMessages parsed = parseMessages(root.path("messages"));
            if (parsed.userPrompt().isBlank() && parsed.systemPrompt().isBlank()) {
                log.warn("[TokenSuppressor] resposta sem mensagens utilizáveis");
                return Optional.empty();
            }

            int saved = root.path("tokens_saved").asInt(0);
            int before = root.path("tokens_before").asInt(0);
            int after = root.path("tokens_after").asInt(0);
            log.info("[TokenSuppressor] userId={} tokens {}→{} saved={} ops={}",
                userId, before, after, saved, root.path("operations_applied"));

            String outSys = parsed.systemPrompt().isBlank() ? sys : parsed.systemPrompt();
            String outUsr = parsed.userPrompt().isBlank() ? usr : parsed.userPrompt();
            return Optional.of(new OptimizedPrompt(outSys, outUsr, saved));
        } catch (Exception e) {
            log.warn("[TokenSuppressor] indisponível, usando prompt original: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private RestTemplate restTemplateWithTimeout() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.min(timeoutMs, 30_000));
        factory.setReadTimeout(timeoutMs);
        return new RestTemplate(factory);
    }

    private static ParsedMessages parseMessages(JsonNode messages) {
        if (messages == null || !messages.isArray()) {
            return new ParsedMessages("", "");
        }
        List<String> systems = new ArrayList<>();
        List<String> users = new ArrayList<>();
        for (JsonNode m : messages) {
            String role = m.path("role").asText("").toLowerCase();
            String content = m.path("content").asText("").trim();
            if (content.isBlank()) {
                continue;
            }
            if ("system".equals(role)) {
                systems.add(content);
            } else if ("user".equals(role)) {
                users.add(content);
            }
        }
        return new ParsedMessages(String.join("\n\n", systems), String.join("\n\n", users));
    }

    private static String trimTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        String u = url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    public record OptimizedPrompt(String systemPrompt, String userPrompt, int tokensSaved) {}

    private record ParsedMessages(String systemPrompt, String userPrompt) {}
}
