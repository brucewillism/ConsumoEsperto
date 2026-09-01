package com.consumoesperto.mobilecapture.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeviceTokenHasherTest {

  @Test
  void generateToken_prefixoEComprimento() {
    String token = DeviceTokenHasher.generateToken();
    assertTrue(token.startsWith("ce_mcd_"));
    assertTrue(token.length() > 20);
  }

  @Test
  void hashToken_deterministico() {
    String raw = "ce_mcd_abc123";
    assertEquals(DeviceTokenHasher.hashToken(raw), DeviceTokenHasher.hashToken(raw));
  }

  @Test
  void hashToken_tokensDiferentes_hashesDiferentes() {
    assertNotEquals(
        DeviceTokenHasher.hashToken(DeviceTokenHasher.generateToken()),
        DeviceTokenHasher.hashToken(DeviceTokenHasher.generateToken())
    );
  }
}
