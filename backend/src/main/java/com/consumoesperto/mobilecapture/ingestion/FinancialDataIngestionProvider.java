package com.consumoesperto.mobilecapture.ingestion;

import com.consumoesperto.mobilecapture.dto.MobileIngestionResultDto;
import com.consumoesperto.mobilecapture.dto.MobileTransactionIngestionRequest;
import com.consumoesperto.model.MobileCaptureDevice;

public interface FinancialDataIngestionProvider {

  String providerKey();

  /**
   * Futuro: reconciliar transação Open Finance com evento mobile sem duplicar.
   */
  MobileIngestionResultDto reconcileExternalTransaction(
      Long usuarioId,
      String externalTransactionId,
      MobileTransactionIngestionRequest payload
  );
}
