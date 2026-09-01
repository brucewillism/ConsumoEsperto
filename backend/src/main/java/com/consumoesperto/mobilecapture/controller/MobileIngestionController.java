package com.consumoesperto.mobilecapture.controller;

import com.consumoesperto.config.MobileCaptureProperties;
import com.consumoesperto.mobilecapture.dto.MobileIngestionResultDto;
import com.consumoesperto.mobilecapture.dto.MobileTransactionIngestionRequest;
import com.consumoesperto.mobilecapture.ingestion.TransactionIngestionService;
import com.consumoesperto.mobilecapture.security.MobileCaptureException;
import com.consumoesperto.mobilecapture.security.MobileDeviceAuthentication;
import com.consumoesperto.mobilecapture.security.MobileDeviceTokenFilter;
import com.consumoesperto.mobilecapture.security.MobileIngestionRateLimiter;
import com.consumoesperto.model.MobileCaptureDevice;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/ingestion/mobile")
@RequiredArgsConstructor
@Slf4j
public class MobileIngestionController {

  private final TransactionIngestionService ingestionService;
  private final MobileCaptureProperties properties;
  private final MobileIngestionRateLimiter rateLimiter;
  private final ObjectMapper objectMapper;

  @PostMapping("/transactions")
  public ResponseEntity<MobileIngestionResultDto> ingest(
      @RequestBody byte[] body,
      HttpServletRequest request
  ) {
    if (body != null && body.length > properties.getMaxPayloadBytes()) {
      return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
          .body(MobileIngestionResultDto.builder().message("Payload excede o limite").build());
    }
    MobileCaptureDevice device = requireDevice();
    rateLimiter.checkOrThrow(device.getId());
    MobileTransactionIngestionRequest payload;
    try {
      payload = objectMapper.readValue(body, MobileTransactionIngestionRequest.class);
    } catch (Exception e) {
      throw new MobileCaptureException("Payload JSON inválido");
    }
    if (!isSourceAllowed(payload.getSource())) {
      throw new MobileCaptureException("Fonte não habilitada");
    }
    String clientEventId = request.getHeader(MobileDeviceTokenFilter.CLIENT_EVENT_HEADER);
    long started = System.nanoTime();
    MobileIngestionResultDto result = ingestionService.ingest(device, payload, clientEventId);
    log.info("mobile_ingestion device_id={} source={} status={} latency_ms={}",
        device.getId(),
        payload.getSource(),
        result.getStatus(),
        (System.nanoTime() - started) / 1_000_000);
    return ResponseEntity.ok(result);
  }

  private boolean isSourceAllowed(String source) {
    if (source == null) {
      return true;
    }
    String normalized = source.trim().toUpperCase();
    if ("TEST".equals(normalized)) {
      return true;
    }
    if ("ANDROID_NOTIFICATION".equals(normalized)) {
      return properties.isAndroidEnabled();
    }
    if ("IOS_WALLET".equals(normalized) || "IOS_SHORTCUTS".equals(normalized)) {
      return properties.isIosEnabled();
    }
    return true;
  }

  private static MobileCaptureDevice requireDevice() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof MobileDeviceAuthentication deviceAuth) {
      return deviceAuth.getDevice();
    }
    throw new MobileCaptureException("Dispositivo não autenticado");
  }
}
