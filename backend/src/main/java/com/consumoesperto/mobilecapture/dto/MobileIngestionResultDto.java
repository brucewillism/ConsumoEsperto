package com.consumoesperto.mobilecapture.dto;

import com.consumoesperto.model.IngestionEventStatus;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class MobileIngestionResultDto {
  Long eventId;
  IngestionEventStatus status;
  Long transacaoId;
  String message;
  BigDecimal amount;
  String merchantNormalized;
  String fingerprintPrefix;
}
