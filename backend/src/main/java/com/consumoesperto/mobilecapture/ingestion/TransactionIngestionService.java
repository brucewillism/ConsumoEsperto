package com.consumoesperto.mobilecapture.ingestion;

import com.consumoesperto.mobilecapture.dto.MobileIngestionResultDto;
import com.consumoesperto.mobilecapture.dto.MobileTransactionIngestionRequest;
import com.consumoesperto.mobilecapture.parser.MobileNotificationParsingService;
import com.consumoesperto.mobilecapture.parser.ParsedMobileTransaction;
import com.consumoesperto.mobilecapture.service.*;
import com.consumoesperto.config.MobileCaptureProperties;
import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.model.*;
import com.consumoesperto.repository.MobileCaptureEventRepository;
import com.consumoesperto.service.TransacaoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Ponto único de ingestão de transações — todas as fontes convergem aqui.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionIngestionService {

  private final MobileCaptureProperties properties;
  private final MobileNotificationParsingService parsingService;
  private final MerchantNormalizationService merchantNormalizationService;
  private final MobileAccountResolverService accountResolver;
  private final MobileIngestionDeduplicationService deduplicationService;
  private final MerchantCategoryRuleService categoryRuleService;
  private final MobileCaptureClassificationService classificationService;
  private final TransacaoService transacaoService;
  private final MobileCaptureEventRepository eventRepository;
  private final com.consumoesperto.repository.MobileCaptureDeviceRepository deviceRepository;

  @Transactional
  public MobileIngestionResultDto ingest(
      MobileCaptureDevice device,
      MobileTransactionIngestionRequest request,
      String headerClientEventId
  ) {
    String clientEventId = firstNonBlank(request.getClientEventId(), headerClientEventId);

    if (clientEventId != null && !clientEventId.isBlank()) {
      Optional<Long> earlyDup = deduplicationService.findDuplicateEventId(device, clientEventId);
      if (earlyDup.isPresent()) {
        return MobileIngestionResultDto.builder()
            .eventId(earlyDup.get())
            .status(IngestionEventStatus.DUPLICATE)
            .message("Evento duplicado — nenhuma nova transação")
            .build();
      }
    }

    MobileCaptureEvent event = newEvent(device, request, clientEventId);
    event.setStatus(IngestionEventStatus.RECEIVED);
    event = eventRepository.save(event);

    device.setLastSeenAt(LocalDateTime.now());
    deviceRepository.save(device);

    try {
      return ingestInternal(device, request, clientEventId, event);
    } catch (org.springframework.dao.DataIntegrityViolationException dup) {
      Optional<Long> dupClient = deduplicationService.findDuplicateEventId(device, clientEventId);
      if (dupClient.isPresent()) {
        return markDuplicate(event, dupClient.get());
      }
      throw dup;
    }
  }

  private MobileIngestionResultDto ingestInternal(
      MobileCaptureDevice device,
      MobileTransactionIngestionRequest request,
      String clientEventId,
      MobileCaptureEvent event
  ) {
    Long usuarioId = device.getUsuario().getId();

    if (isTestSource(request)) {
      device.setLastTestOkAt(LocalDateTime.now());
      deviceRepository.save(device);
      event.setStatus(IngestionEventStatus.REGISTERED);
      event.setProcessedAt(LocalDateTime.now());
      event.setErrorMessage("TEST_OK");
      eventRepository.save(event);
      log.info("mobile_capture_test_ok device_id={} usuario_id={}", device.getId(), usuarioId);
      return MobileIngestionResultDto.builder()
          .eventId(event.getId())
          .status(IngestionEventStatus.REGISTERED)
          .message("TEST_OK")
          .build();
    }

    Optional<Long> dupClient = deduplicationService.findDuplicateEventId(device, clientEventId);
    if (dupClient.isPresent() && !dupClient.get().equals(event.getId())) {
      return markDuplicate(event, dupClient.get());
    }

    Optional<ParsedMobileTransaction> parsed = parsingService.parse(request);
    if (parsed.isEmpty()) {
      event.setStatus(IngestionEventStatus.NEEDS_REVIEW);
      event.setProcessedAt(LocalDateTime.now());
      event.setErrorMessage("Não foi possível interpretar o evento com segurança");
      eventRepository.save(event);
      return result(event, "Aguardando revisão manual");
    }

    ParsedMobileTransaction p = parsed.get();
    event.setStatus(IngestionEventStatus.PARSED);
    event.setParserName(p.getParserName());
    event.setAmount(p.getAmount());
    event.setCurrency(p.getCurrency());
    event.setMerchantRaw(p.getMerchantRaw());
    String merchantNorm = merchantNormalizationService.normalize(
        p.getMerchant() != null ? p.getMerchant() : p.getMerchantRaw());
    event.setMerchantNormalized(merchantNorm);

    if (!p.isConfident() || p.getAmount() == null || merchantNorm == null || merchantNorm.isBlank()) {
      event.setStatus(IngestionEventStatus.NEEDS_REVIEW);
      event.setProcessedAt(LocalDateTime.now());
      eventRepository.save(event);
      return result(event, "Dados insuficientes — revisão necessária");
    }

    OrigemTransacao origem = mapOrigem(request.getSource());
    Optional<MobileAccountResolverService.ResolvedAccount> account = accountResolver.resolve(
        usuarioId, device.getId(), request.getPackageName(), p.getCardHint());

    Long contaId = account.map(MobileAccountResolverService.ResolvedAccount::contaId).orElse(null);
    Long cartaoId = account.map(MobileAccountResolverService.ResolvedAccount::cartaoId).orElse(null);

    LocalDateTime occurredAt = p.getOccurredAt() != null ? p.getOccurredAt() : LocalDateTime.now();
    String fingerprint = deduplicationService.buildFingerprint(
        usuarioId, origem, contaId, cartaoId, p.getAmount(), merchantNorm, occurredAt);
    event.setFingerprint(fingerprint);

    if (deduplicationService.existsRegisteredTransaction(usuarioId, fingerprint)) {
      return markDuplicate(event, event.getId());
    }
    Optional<Long> dupFp = deduplicationService.findDuplicateByFingerprint(usuarioId, fingerprint);
    if (dupFp.isPresent() && !dupFp.get().equals(event.getId())) {
      return markDuplicate(event, dupFp.get());
    }

    CategoryResolution category = resolveCategory(usuarioId, merchantNorm, p.getAmount());

    TransacaoDTO dto = new TransacaoDTO();
    dto.setDescricao(merchantNorm.length() > 200 ? merchantNorm.substring(0, 200) : merchantNorm);
    dto.setValor(p.getAmount());
    dto.setTipoTransacao(TransacaoDTO.TipoTransacao.DESPESA);
    dto.setDataTransacao(occurredAt);
    dto.setContaBancariaId(contaId);
    if (cartaoId != null) {
      dto.setCartaoCreditoId(cartaoId);
    }
    if (category.categoriaId != null && category.autoApply) {
      dto.setCategoriaId(category.categoriaId);
    }
    dto.setStatusConferencia(category.autoApply
        ? TransacaoDTO.StatusConferencia.CONFIRMADA
        : TransacaoDTO.StatusConferencia.PENDENTE);

    TransacaoDTO created = transacaoService.criarTransacao(dto, usuarioId, false, true, true);
    attachIngestionMetadata(created.getId(), origem, clientEventId, request.getPackageName(),
        p.getMerchantRaw(), merchantNorm, fingerprint, category.confidence, event.getId());

    event.setStatus(IngestionEventStatus.REGISTERED);
    event.setTransacaoId(created.getId());
    event.setConfidence(category.confidence);
    event.setProcessedAt(LocalDateTime.now());
    eventRepository.save(event);

    log.info("mobile_capture_registered device_id={} event_id={} transacao_id={} parser={} fp={}",
        device.getId(), event.getId(), created.getId(), p.getParserName(),
        deduplicationService.fingerprintPrefix(fingerprint));

    return MobileIngestionResultDto.builder()
        .eventId(event.getId())
        .status(IngestionEventStatus.REGISTERED)
        .transacaoId(created.getId())
        .amount(p.getAmount())
        .merchantNormalized(merchantNorm)
        .fingerprintPrefix(deduplicationService.fingerprintPrefix(fingerprint))
        .message("Transação registrada")
        .build();
  }

  private void attachIngestionMetadata(
      Long transacaoId,
      OrigemTransacao origem,
      String externalEventId,
      String provider,
      String merchantRaw,
      String merchantNorm,
      String fingerprint,
      BigDecimal confidence,
      Long eventId
  ) {
    if (transacaoId == null) {
      return;
    }
    transacaoService.atualizarMetadadosIngestao(
        transacaoId, origem, externalEventId, provider, merchantRaw, merchantNorm, fingerprint, confidence, eventId);
  }

  private CategoryResolution resolveCategory(Long usuarioId, String merchantNorm, BigDecimal amount) {
    Optional<MerchantCategoryRuleService.CategoryMatch> rule = categoryRuleService.match(usuarioId, merchantNorm);
    if (rule.isPresent()) {
      BigDecimal conf = rule.get().confidence();
      return new CategoryResolution(rule.get().categoriaId(), conf, shouldAutoApply(conf), conf);
    }
    Optional<MerchantCategoryRuleService.CategoryMatch> edith =
        classificationService.classify(usuarioId, merchantNorm, amount);
    if (edith.isPresent()) {
      BigDecimal conf = edith.get().confidence();
      return new CategoryResolution(edith.get().categoriaId(), conf, shouldAutoApply(conf), conf);
    }
    return new CategoryResolution(null, BigDecimal.ZERO, false, BigDecimal.ZERO);
  }

  private boolean shouldAutoApply(BigDecimal confidence) {
    return confidence != null
        && confidence.compareTo(BigDecimal.valueOf(properties.getAutoCategoryThreshold())) >= 0;
  }

  private MobileIngestionResultDto markDuplicate(MobileCaptureEvent event, Long originalEventId) {
    event.setStatus(IngestionEventStatus.DUPLICATE);
    event.setProcessedAt(LocalDateTime.now());
    event.setErrorMessage("DUPLICATE_OF_" + originalEventId);
    eventRepository.save(event);
    return MobileIngestionResultDto.builder()
        .eventId(event.getId())
        .status(IngestionEventStatus.DUPLICATE)
        .message("Evento duplicado — nenhuma nova transação")
        .fingerprintPrefix(deduplicationService.fingerprintPrefix(event.getFingerprint()))
        .build();
  }

  private MobileCaptureEvent newEvent(
      MobileCaptureDevice device,
      MobileTransactionIngestionRequest request,
      String clientEventId
  ) {
    MobileCaptureEvent event = new MobileCaptureEvent();
    event.setDevice(device);
    event.setUsuario(device.getUsuario());
    event.setSource(request.getSource() == null ? "UNKNOWN" : request.getSource().trim().toUpperCase());
    event.setClientEventId(blankToNull(clientEventId));
    event.setPackageName(request.getPackageName());
    event.setNotificationTitle(truncate(request.getNotificationTitle(), 300));
    event.setNotificationText(truncate(request.getNotificationText(), 1000));
    event.setCardHint(request.getCardHint());
    return event;
  }

  private static OrigemTransacao mapOrigem(String source) {
    return mapOrigemForReview(source);
  }

  public static OrigemTransacao mapOrigemForReview(String source) {
    if (source == null) {
      return OrigemTransacao.MANUAL;
    }
    return switch (source.trim().toUpperCase()) {
      case "ANDROID_NOTIFICATION" -> OrigemTransacao.ANDROID_NOTIFICATION;
      case "IOS_WALLET", "IOS_SHORTCUTS" -> OrigemTransacao.IOS_WALLET;
      case "WHATSAPP" -> OrigemTransacao.WHATSAPP;
      case "FATURA_PDF" -> OrigemTransacao.FATURA_PDF;
      case "PIX" -> OrigemTransacao.PIX;
      case "OPEN_FINANCE" -> OrigemTransacao.OPEN_FINANCE;
      default -> OrigemTransacao.MANUAL;
    };
  }

  private static boolean isTestSource(MobileTransactionIngestionRequest request) {
    return request.getSource() != null && "TEST".equalsIgnoreCase(request.getSource().trim());
  }

  private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) {
      return a.trim();
    }
    if (b != null && !b.isBlank()) {
      return b.trim();
    }
    return null;
  }

  private static String blankToNull(String v) {
    return v == null || v.isBlank() ? null : v.trim();
  }

  private static String truncate(String v, int max) {
    if (v == null) {
      return null;
    }
    return v.length() <= max ? v : v.substring(0, max);
  }

  private static MobileIngestionResultDto result(MobileCaptureEvent event, String message) {
    return MobileIngestionResultDto.builder()
        .eventId(event.getId())
        .status(event.getStatus())
        .message(message)
        .amount(event.getAmount())
        .merchantNormalized(event.getMerchantNormalized())
        .build();
  }

  private record CategoryResolution(Long categoriaId, BigDecimal confidence, boolean autoApply, BigDecimal displayConfidence) {}
}
