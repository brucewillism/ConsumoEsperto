package com.consumoesperto.edith;

import com.consumoesperto.edith.security.EdithHmacSigner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vetor HMAC congelado — assinatura ConsumoEsperto alinhada ao contrato E.D.I.T.H. SDK 0.4.1
 * (sem dependência do artefato Maven, indisponível no Central).
 */
class EdithHmacContractTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void hmacMatchesFrozenEdithContractFixture() throws Exception {
    JsonNode fixture = loadFixture();
    String secret = fixture.get("secret").asText();
    String timestamp = fixture.get("timestamp").asText();
    String nonce = fixture.get("nonce").asText();
    String requestId = fixture.get("request_id").asText();
    byte[] body = fixture.get("body").asText().getBytes(StandardCharsets.UTF_8);
    String expected = fixture.get("expected_signature").asText();

    String localSignature = EdithHmacSigner.sign(secret, timestamp, nonce, requestId, body);

    assertEquals(expected, localSignature, "Assinatura local deve bater com o vetor congelado E.D.I.T.H.");
    assertTrue(EdithHmacSigner.signaturesMatch(expected, localSignature));
  }

  @Test
  void signatureComparisonIsCaseInsensitive() {
    String a = "AbCdEf";
    String b = "abcdef";
    assertEquals(true, EdithHmacSigner.signaturesMatch(a, b));
  }

  private static JsonNode loadFixture() throws Exception {
    try (InputStream in = EdithHmacContractTest.class.getResourceAsStream("/edith/hmac-fixture.json")) {
      if (in == null) {
        throw new IllegalStateException("Fixture /edith/hmac-fixture.json ausente");
      }
      return MAPPER.readTree(in);
    }
  }
}
