package com.consumoesperto.mobilecapture.dto;

import lombok.Data;

@Data
public class CreateMobileSourceMappingRequest {
  private Long deviceId;
  private String packageName;
  private String providerKey;
  private String cardLast4;
  private Long contaId;
  private Long cartaoId;
}
