package com.consumoesperto.mobilecapture.dto;

import com.consumoesperto.model.MobilePlatform;
import lombok.Value;

@Value
public class MobileDeviceRegistrationResponse {
  Long deviceId;
  String deviceToken;
  String ingestionUrl;
  MobilePlatform platform;
  String name;
}
