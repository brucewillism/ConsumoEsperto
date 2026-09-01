package com.consumoesperto.mobilecapture.dto;

import com.consumoesperto.model.MobilePlatform;
import lombok.Data;

@Data
public class CreateMobileCaptureDeviceRequest {
  private String name;
  private MobilePlatform platform;
}
