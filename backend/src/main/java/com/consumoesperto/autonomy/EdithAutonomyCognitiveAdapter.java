package com.consumoesperto.autonomy;

import com.consumoesperto.config.EdithProperties;
import com.consumoesperto.config.FinancialAutonomyProperties;
import com.consumoesperto.edith.CognitiveGatewaySelector;
import com.consumoesperto.edith.CognitiveRequest;
import com.consumoesperto.edith.CognitiveResponse;
import com.consumoesperto.edith.EdithCognitiveGateway;
import com.consumoesperto.edith.PromptSanitize;
import com.consumoesperto.model.Categoria;
import com.consumoesperto.repository.CategoriaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Classificação via E.D.I.T.H. apenas. Sem fallback OpenAI/Groq/Gemini.
 * Resolve o rótulo devolvido ao catálogo do usuário; não inventa categoria local.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EdithAutonomyCognitiveAdapter implements AutonomyCognitivePort {

    private final EdithProperties edithProperties;
    private final FinancialAutonomyProperties autonomyProperties;
    private final CognitiveGatewaySelector gatewaySelector;
    private final EdithCognitiveGateway edithCognitiveGateway;
    private final CategoriaRepository categoriaRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Optional<ClassificationHint> classifyMerchant(Long usuarioId, String merchant, String descricao) {
        if (!autonomyProperties.isEdith() || !edithProperties.isEnabled() || !gatewaySelector.usesEdith()) {
            return Optional.empty();
        }
        try {
            List<Categoria> catalogo = categoriaRepository.findByUsuarioIdOrderByNome(usuarioId);
            String categories = catalogo.stream()
                .limit(40)
                .map(c -> c.getId() + ":" + c.getNome())
                .collect(Collectors.joining(", "));
            String merchantSafe = PromptSanitize.userText(merchant != null ? merchant : descricao);
            String prompt = """
                Classifique a despesa. JSON estrito: {"categoryId":number,"category":"nome","confidence":0.0-1.0}
                Use somente uma categoria da lista. merchant=%s categorias=[%s]
                """.formatted(merchantSafe, categories);
            long timeoutMs = Math.max(1_000L, autonomyProperties.getEdithClassifyTimeoutMs());
            CognitiveResponse response = edithCognitiveGateway.send(CognitiveRequest.builder()
                .usuarioId(usuarioId)
                .content(prompt)
                .sourceAction(EdithSourceActions.TRANSACTION_CLASSIFICATION)
                .awaitCompletion(true)
                .timeoutMs(timeoutMs)
                .build());
            if (response == null || response.getResultText() == null || response.getResultText().isBlank()) {
                return Optional.empty();
            }
            JsonNode node = objectMapper.readTree(extractJson(response.getResultText()));
            Categoria matched = resolveCategoria(usuarioId, catalogo, node);
            if (matched == null) {
                return Optional.empty();
            }
            BigDecimal confidence = BigDecimal.valueOf(node.path("confidence").asDouble(0));
            return Optional.of(new ClassificationHint(
                matched.getId(),
                matched.getNome(),
                confidence,
                response.getTaskId(),
                response.getConversationId(),
                firstNonBlank(response.getRequestId(), response.getClientRequestId())
            ));
        } catch (Exception e) {
            log.warn("edith_autonomy_classify_unavailable userId={} err={}", usuarioId, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Categoria resolveCategoria(Long usuarioId, List<Categoria> catalogo, JsonNode node) {
        long categoryId = node.path("categoryId").asLong(0);
        if (categoryId <= 0) {
            categoryId = node.path("categoriaId").asLong(0);
        }
        if (categoryId > 0) {
            return categoriaRepository.findByIdAndUsuarioId(categoryId, usuarioId).orElse(null);
        }
        String label = firstNonBlank(
            text(node, "category"),
            text(node, "categoria"),
            text(node, "nome")
        );
        if (label == null) {
            return null;
        }
        String folded = fold(label);
        List<Categoria> hits = catalogo.stream()
            .filter(c -> c.getNome() != null && fold(c.getNome()).equals(folded))
            .toList();
        return hits.size() == 1 ? hits.get(0) : null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        String s = v.asText();
        return s == null || s.isBlank() ? null : s;
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) {
            return null;
        }
        for (String v : vals) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    static String fold(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    private static String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }
}
