package com.consumoesperto.mobilecapture.controller;

import com.consumoesperto.mobilecapture.dto.CreateMobileCaptureDeviceRequest;
import com.consumoesperto.mobilecapture.dto.CreateMobileSourceMappingRequest;
import com.consumoesperto.mobilecapture.dto.MobileCaptureDeviceDto;
import com.consumoesperto.mobilecapture.dto.MobileDeviceRegistrationResponse;
import com.consumoesperto.mobilecapture.dto.MobileSourceMappingDto;
import com.consumoesperto.mobilecapture.service.MobileCaptureDeviceService;
import com.consumoesperto.mobilecapture.service.MobileSourceMappingService;
import com.consumoesperto.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mobile-capture")
@RequiredArgsConstructor
public class MobileCaptureDeviceController {

  private final MobileCaptureDeviceService deviceService;
  private final MobileSourceMappingService mappingService;

  @GetMapping("/devices")
  public ResponseEntity<List<MobileCaptureDeviceDto>> listDevices(@AuthenticationPrincipal UserPrincipal user) {
    return ResponseEntity.ok(deviceService.listDevices(user.getId()));
  }

  @PostMapping("/devices")
  public ResponseEntity<MobileDeviceRegistrationResponse> register(
      @AuthenticationPrincipal UserPrincipal user,
      @RequestBody CreateMobileCaptureDeviceRequest request
  ) {
    return ResponseEntity.ok(deviceService.register(user.getId(), request));
  }

  @PostMapping("/devices/{id}/revoke")
  public ResponseEntity<Map<String, String>> revoke(
      @AuthenticationPrincipal UserPrincipal user,
      @PathVariable Long id
  ) {
    deviceService.revoke(user.getId(), id);
    return ResponseEntity.ok(Map.of("status", "REVOKED"));
  }

  @PostMapping("/devices/{id}/rotate-token")
  public ResponseEntity<MobileDeviceRegistrationResponse> rotateToken(
      @AuthenticationPrincipal UserPrincipal user,
      @PathVariable Long id
  ) {
    return ResponseEntity.ok(deviceService.rotateToken(user.getId(), id));
  }

  @GetMapping("/source-mappings")
  public ResponseEntity<List<MobileSourceMappingDto>> listMappings(@AuthenticationPrincipal UserPrincipal user) {
    return ResponseEntity.ok(mappingService.list(user.getId()));
  }

  @PostMapping("/source-mappings")
  public ResponseEntity<MobileSourceMappingDto> createMapping(
      @AuthenticationPrincipal UserPrincipal user,
      @RequestBody CreateMobileSourceMappingRequest request
  ) {
    return ResponseEntity.ok(mappingService.create(user.getId(), request));
  }

  @DeleteMapping("/source-mappings/{id}")
  public ResponseEntity<Void> deleteMapping(
      @AuthenticationPrincipal UserPrincipal user,
      @PathVariable Long id
  ) {
    mappingService.delete(user.getId(), id);
    return ResponseEntity.noContent().build();
  }
}
