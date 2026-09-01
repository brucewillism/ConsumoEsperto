package com.consumoesperto.mobilecapture.service;

import com.consumoesperto.config.MobileCaptureProperties;
import com.consumoesperto.edith.CognitiveGatewaySelector;
import com.consumoesperto.edith.CognitiveRequest;
import com.consumoesperto.edith.CognitiveResponse;
import com.consumoesperto.repository.CategoriaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Classificação via E.D.I.T.H. — somente leitura; ConsumoEsperto valida ownership e grava.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MobileCaptureClassificationService {

  private final MobileCaptureProperties properties;
  private final CognitiveGatewaySelector gatewaySelector;
  private final CategoriaRepository categoriaRepository;
  private final ObjectMapper objectMapper;

  public Optional<MerchantCategoryRuleService.CategoryMatch> classify(
      Long usuarioId,
      String merchantNormalized,
      BigDecimal amount
  ) {
    if (!properties.isEdithClassificationEnabled()) {
      return Optional.empty();
    }
    try {
      String categories = categoriaRepository.findByUsuarioIdOrderByNome(usuarioId).stream()
          .map(c -> c.getId() + ":" + c.getNome())
          .collect(Collectors.joining(", "));
      String prompt = """
          Classifique a despesa para JSON estrito: {"categoryId":number,"confidence":0.0-1.0}
          merchant=%s amount=%s categorias=[%s]
          """.formatted(merchantNormalized, amount, categories);
      CognitiveResponse response = gatewaySelector.active().send(CognitiveRequest.builder()
          .usuarioId(usuarioId)
          .content(prompt)
          .sourceAction("consumo.transaction_classification")
          .awaitCompletion(true)
          .build());
      if (response == null || response.getResultText() == null || response.getResultText().isBlank()) {
        return Optional.empty();
      }
      JsonNode node = objectMapper.readTree(extractJson(response.getResultText()));
      long categoryId = node.path("categoryId").asLong(0);
      if (categoryId <= 0) {
        return Optional.empty();
      }
      if (categoriaRepository.findByIdAndUsuarioId(categoryId, usuarioId).isEmpty()) {
        log.warn("mobile_capture_edith_category_rejected usuarioId={} categoryId={}", usuarioId, categoryId);
        return Optional.empty();
      }
      BigDecimal confidence = BigDecimal.valueOf(node.path("confidence").asDouble(0.75));
      return Optional.of(new MerchantCategoryRuleService.CategoryMatch(categoryId, confidence, "EDITH"));
    } catch (Exception e) {
      log.warn("mobile_capture_edith_classify_failed usuarioId={} err={}", usuarioId, e.getClass().getSimpleName());
      return Optional.empty();
    }
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
