package com.consumoesperto.mobilecapture.dto;

import lombok.Data;

@Data
public class ConfirmMobileCaptureEventRequest {
  private String merchant;
  private Long contaBancariaId;
  private Long cartaoCreditoId;
  private Long categoriaId;
  private boolean saveMerchantCategoryRule;
}
