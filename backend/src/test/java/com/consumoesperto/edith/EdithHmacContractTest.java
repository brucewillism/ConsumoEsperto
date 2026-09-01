package com.consumoesperto.edith;

import com.consumoesperto.edith.security.EdithHmacSigner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.edith.sdk.tools.EdithToolAuthenticator;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Vetor HMAC congelado — assinatura ConsumoEsperto deve ser bit-a-bit igual à E.D.I.T.H. SDK 0.4.1.
 */
class EdithHmacContractTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void hmacMatchesEdithSdkFixture() throws Exception {
    JsonNode fixture = loadFixture();
    String secret = fixture.get("secret").asText();
    String timestamp = fixture.get("timestamp").asText();
    String nonce = fixture.get("nonce").asText();
    String requestId = fixture.get("request_id").asText();
    byte[] body = fixture.get("body").asText().getBytes(StandardCharsets.UTF_8);

    EdithToolAuthenticator sdk = new EdithToolAuthenticator(secret);
    String sdkSignature = sdk.sign(timestamp, nonce, requestId, body);
    String localSignature = EdithHmacSigner.sign(secret, timestamp, nonce, requestId, body);

    assertEquals(sdkSignature, localSignature, "Assinatura local deve ser idêntica ao SDK E.D.I.T.H.");
    assertEquals(
      EdithHmacSigner.sha256Hex(body),
      EdithHmacSigner.sha256Hex(body),
      "SHA-256 do body raw deve ser estável"
    );
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
