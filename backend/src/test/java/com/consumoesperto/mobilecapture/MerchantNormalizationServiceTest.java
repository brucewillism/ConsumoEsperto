package com.consumoesperto.mobilecapture.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MerchantNormalizationServiceTest {

  private final MerchantNormalizationService service = new MerchantNormalizationService();

  @Test
  void normalizaVariantesShell() {
    String a = service.normalize("POSTO SHELL 1234 RECIFE");
    String b = service.normalize("POSTO SHELL*1234");
    String c = service.normalize("SHELL RECIFE");
    assertNotNull(a);
    assertTrue(a.contains("SHELL"));
    assertEquals(a, service.normalize("posto shell 1234 recife"));
    assertNotNull(b);
    assertNotNull(c);
  }
}
