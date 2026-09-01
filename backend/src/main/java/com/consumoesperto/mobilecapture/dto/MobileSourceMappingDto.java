package com.consumoesperto.mobilecapture.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MobileSourceMappingDto {
  Long id;
  Long deviceId;
  String packageName;
  String providerKey;
  String cardLast4;
  Long contaId;
  Long cartaoId;
  boolean enabled;
}
