package com.consumoesperto.mobilecapture.dto;

import com.consumoesperto.model.IngestionEventStatus;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Value
@Builder
public class MobileCaptureEventReviewDto {
  Long id;
  String source;
  IngestionEventStatus status;
  BigDecimal amount;
  String currency;
  String merchantRaw;
  String merchantNormalized;
  String packageName;
  String cardHint;
  String parserName;
  LocalDateTime receivedAt;
}
