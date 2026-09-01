package com.consumoesperto.mobilecapture.dto;

import com.consumoesperto.model.MobilePlatform;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class MobileCaptureDeviceDto {
  Long id;
  String name;
  MobilePlatform platform;
  boolean active;
  LocalDateTime createdAt;
  LocalDateTime lastSeenAt;
  LocalDateTime revokedAt;
  LocalDateTime lastTestOkAt;
}
