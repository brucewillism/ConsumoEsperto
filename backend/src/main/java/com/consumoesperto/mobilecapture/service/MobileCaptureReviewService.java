package com.consumoesperto.mobilecapture.service;

import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.exception.ResourceNotFoundException;
import com.consumoesperto.mobilecapture.dto.ConfirmMobileCaptureEventRequest;
import com.consumoesperto.mobilecapture.dto.MobileCaptureEventReviewDto;
import com.consumoesperto.mobilecapture.dto.MobileIngestionResultDto;
import com.consumoesperto.mobilecapture.ingestion.TransactionIngestionService;
import com.consumoesperto.model.IngestionEventStatus;
import com.consumoesperto.model.MobileCaptureEvent;
import com.consumoesperto.model.OrigemTransacao;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.CategoriaRepository;
import com.consumoesperto.repository.ContaBancariaRepository;
import com.consumoesperto.repository.MobileCaptureEventRepository;
import com.consumoesperto.service.TransacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MobileCaptureReviewService {

  private final MobileCaptureFeatureGuard featureGuard;
  private final MobileCaptureEventRepository eventRepository;
  private final MerchantNormalizationService normalizationService;
  private final MobileIngestionDeduplicationService deduplicationService;
  private final MerchantCategoryRuleService categoryRuleService;
  private final TransacaoService transacaoService;
  private final ContaBancariaRepository contaBancariaRepository;
  private final CartaoCreditoRepository cartaoCreditoRepository;
  private final CategoriaRepository categoriaRepository;

  public List<MobileCaptureEventReviewDto> listNeedsReview(Long usuarioId) {
    featureGuard.requireEnabled();
    return eventRepository.findByUsuarioIdAndStatusOrderByReceivedAtDesc(
            usuarioId, IngestionEventStatus.NEEDS_REVIEW).stream()
        .map(this::toDto)
        .toList();
  }

  @Transactional
  public MobileIngestionResultDto confirm(Long usuarioId, Long eventId, ConfirmMobileCaptureEventRequest request) {
    featureGuard.requireEnabled();
    MobileCaptureEvent event = eventRepository.findByIdAndUsuarioId(eventId, usuarioId)
        .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado"));
    if (event.getStatus() != IngestionEventStatus.NEEDS_REVIEW) {
      throw new IllegalStateException("Evento não está pendente de revisão");
    }
    if (event.getAmount() == null || event.getAmount().signum() <= 0) {
      throw new IllegalArgumentException("Valor inválido para registro");
    }
    String merchant = request.getMerchant();
    if (merchant == null || merchant.isBlank()) {
      merchant = event.getMerchantNormalized() != null ? event.getMerchantNormalized() : event.getMerchantRaw();
    }
    String merchantNorm = normalizationService.normalize(merchant);
    if (merchantNorm == null || merchantNorm.isBlank()) {
      throw new IllegalArgumentException("Estabelecimento obrigatório");
    }
    Long contaId = request.getContaBancariaId();
    Long cartaoId = request.getCartaoCreditoId();
    if (contaId != null && contaBancariaRepository.findByIdAndUsuarioId(contaId, usuarioId).isEmpty()) {
      throw new IllegalArgumentException("Conta inválida");
    }
    if (cartaoId != null && cartaoCreditoRepository.findByIdAndUsuarioId(cartaoId, usuarioId).isEmpty()) {
      throw new IllegalArgumentException("Cartão inválido");
    }
    if (request.getCategoriaId() != null
        && categoriaRepository.findByIdAndUsuarioId(request.getCategoriaId(), usuarioId).isEmpty()) {
      throw new IllegalArgumentException("Categoria inválida");
    }

    OrigemTransacao origem = TransactionIngestionService.mapOrigemForReview(event.getSource());
    LocalDateTime occurredAt = event.getReceivedAt() != null ? event.getReceivedAt() : LocalDateTime.now();
    String fingerprint = deduplicationService.buildFingerprint(
        usuarioId, origem, contaId, cartaoId, event.getAmount(), merchantNorm, occurredAt);
    event.setFingerprint(fingerprint);
    event.setMerchantNormalized(merchantNorm);

    if (deduplicationService.existsRegisteredTransaction(usuarioId, fingerprint)) {
      event.setStatus(IngestionEventStatus.DUPLICATE);
      event.setProcessedAt(LocalDateTime.now());
      eventRepository.save(event);
      return MobileIngestionResultDto.builder()
          .eventId(event.getId())
          .status(IngestionEventStatus.DUPLICATE)
          .message("Transação já registrada")
          .build();
    }

    TransacaoDTO dto = new TransacaoDTO();
    dto.setDescricao(merchantNorm.length() > 200 ? merchantNorm.substring(0, 200) : merchantNorm);
    dto.setValor(event.getAmount());
    dto.setTipoTransacao(TransacaoDTO.TipoTransacao.DESPESA);
    dto.setDataTransacao(occurredAt);
    dto.setContaBancariaId(contaId);
    dto.setCartaoCreditoId(cartaoId);
    if (request.getCategoriaId() != null) {
      dto.setCategoriaId(request.getCategoriaId());
      dto.setStatusConferencia(TransacaoDTO.StatusConferencia.CONFIRMADA);
    } else {
      dto.setStatusConferencia(TransacaoDTO.StatusConferencia.PENDENTE);
    }

    TransacaoDTO created = transacaoService.criarTransacao(dto, usuarioId, false, true, true);
    transacaoService.atualizarMetadadosIngestao(
        created.getId(),
        origem,
        event.getClientEventId(),
        event.getPackageName(),
        event.getMerchantRaw(),
        merchantNorm,
        fingerprint,
        BigDecimal.ONE,
        event.getId()
    );

    if (request.isSaveMerchantCategoryRule() && request.getCategoriaId() != null) {
      categoryRuleService.saveUserRule(usuarioId, merchantNorm, request.getCategoriaId());
    }

    event.setStatus(IngestionEventStatus.REGISTERED);
    event.setTransacaoId(created.getId());
    event.setConfidence(BigDecimal.ONE);
    event.setProcessedAt(LocalDateTime.now());
    eventRepository.save(event);

    return MobileIngestionResultDto.builder()
        .eventId(event.getId())
        .status(IngestionEventStatus.REGISTERED)
        .transacaoId(created.getId())
        .amount(event.getAmount())
        .merchantNormalized(merchantNorm)
        .message("Transação confirmada")
        .build();
  }

  @Transactional
  public void discard(Long usuarioId, Long eventId) {
    featureGuard.requireEnabled();
    MobileCaptureEvent event = eventRepository.findByIdAndUsuarioId(eventId, usuarioId)
        .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado"));
    if (event.getStatus() != IngestionEventStatus.NEEDS_REVIEW) {
      throw new IllegalStateException("Evento não está pendente de revisão");
    }
    event.setStatus(IngestionEventStatus.REJECTED);
    event.setProcessedAt(LocalDateTime.now());
    eventRepository.save(event);
  }

  private MobileCaptureEventReviewDto toDto(MobileCaptureEvent event) {
    return MobileCaptureEventReviewDto.builder()
        .id(event.getId())
        .source(event.getSource())
        .status(event.getStatus())
        .amount(event.getAmount())
        .currency(event.getCurrency())
        .merchantRaw(event.getMerchantRaw())
        .merchantNormalized(event.getMerchantNormalized())
        .packageName(event.getPackageName())
        .cardHint(event.getCardHint())
        .parserName(event.getParserName())
        .receivedAt(event.getReceivedAt())
        .build();
  }
}
