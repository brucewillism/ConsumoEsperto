package com.consumoesperto.edith;

import com.consumoesperto.config.EdithProperties;
import com.consumoesperto.edith.security.EdithHmacSigner;
import com.consumoesperto.repository.EdithCallbackNonceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EdithCallbackSecurityServiceTest {

  @Mock private EdithProperties properties;
  @Mock private EdithCallbackNonceRepository nonceRepository;

  private EdithCallbackSecurityService service;

  @BeforeEach
  void setUp() {
    service = new EdithCallbackSecurityService(properties, nonceRepository);
    when(properties.isEnabled()).thenReturn(true);
    when(properties.getCallbackSecret()).thenReturn("test-secret");
    when(properties.getCallbackTimestampSkewSeconds()).thenReturn(300L);
    when(nonceRepository.existsByNonce(any())).thenReturn(false);
  }

  @Test
  void validSignatureAccepted() {
    String ts = String.valueOf(Instant.now().getEpochSecond());
    String nonce = "nonce-1";
    String requestId = "req-1";
    byte[] body = "{\"tool\":\"finance.accounts.list\",\"request_id\":\"req-1\",\"arguments\":{\"context_ref\":\"ctx-1\"}}"
      .getBytes(StandardCharsets.UTF_8);
    String sig = EdithHmacSigner.sign("test-secret", ts, nonce, requestId, body);

    assertDoesNotThrow(() -> service.validate(ts, nonce, requestId, body, sig));
  }

  @Test
  void invalidSignatureRejected() {
    String ts = String.valueOf(Instant.now().getEpochSecond());
    byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
    assertThrows(EdithException.class, () ->
      service.validate(ts, "n1", "r1", body, "bad-signature")
    );
  }

  @Test
  void replayNonceRejected() {
    when(nonceRepository.existsByNonce("dup")).thenReturn(true);
    String ts = String.valueOf(Instant.now().getEpochSecond());
    byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
    String sig = EdithHmacSigner.sign("test-secret", ts, "dup", "r1", body);
    assertThrows(EdithException.class, () ->
      service.validate(ts, "dup", "r1", body, sig)
    );
  }
}
