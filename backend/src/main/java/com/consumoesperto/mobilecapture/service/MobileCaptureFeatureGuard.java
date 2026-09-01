package com.consumoesperto.mobilecapture.service;

import com.consumoesperto.config.MobileCaptureProperties;
import com.consumoesperto.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MobileCaptureFeatureGuard {

  private final MobileCaptureProperties properties;

  public void requireEnabled() {
    if (!properties.isEnabled()) {
      throw new ResourceNotFoundException("Captura automática não disponível");
    }
  }
}
