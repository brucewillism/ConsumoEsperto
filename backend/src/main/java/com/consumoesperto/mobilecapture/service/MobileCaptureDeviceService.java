package com.consumoesperto.mobilecapture.service;

import com.consumoesperto.config.MobileCaptureProperties;
import com.consumoesperto.exception.ResourceNotFoundException;
import com.consumoesperto.mobilecapture.dto.CreateMobileCaptureDeviceRequest;
import com.consumoesperto.mobilecapture.dto.MobileCaptureDeviceDto;
import com.consumoesperto.mobilecapture.dto.MobileDeviceRegistrationResponse;
import com.consumoesperto.mobilecapture.security.DeviceTokenHasher;
import com.consumoesperto.model.MobileCaptureDevice;
import com.consumoesperto.model.MobilePlatform;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.MobileCaptureDeviceRepository;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MobileCaptureDeviceService {

  private final MobileCaptureProperties properties;
  private final MobileCaptureFeatureGuard featureGuard;
  private final MobileCaptureDeviceRepository deviceRepository;
  private final UsuarioRepository usuarioRepository;

  public List<MobileCaptureDeviceDto> listDevices(Long usuarioId) {
    featureGuard.requireEnabled();
    return deviceRepository.findByUsuarioIdOrderByCreatedAtDesc(usuarioId).stream()
        .map(this::toDto)
        .toList();
  }

  @Transactional
  public MobileDeviceRegistrationResponse register(Long usuarioId, CreateMobileCaptureDeviceRequest request) {
    featureGuard.requireEnabled();
    if (request.getPlatform() == null) {
      throw new IllegalArgumentException("Plataforma obrigatória");
    }
    if (request.getName() == null || request.getName().isBlank()) {
      throw new IllegalArgumentException("Nome do dispositivo obrigatório");
    }
    Usuario usuario = usuarioRepository.findById(usuarioId)
        .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    String rawToken = DeviceTokenHasher.generateToken();
    MobileCaptureDevice device = new MobileCaptureDevice();
    device.setUsuario(usuario);
    device.setName(request.getName().trim());
    device.setPlatform(request.getPlatform());
    device.setTokenHash(DeviceTokenHasher.hashToken(rawToken));
    device.setActive(true);
    device = deviceRepository.save(device);
    return new MobileDeviceRegistrationResponse(
        device.getId(),
        rawToken,
        buildIngestionUrl(),
        device.getPlatform(),
        device.getName()
    );
  }

  @Transactional
  public void revoke(Long usuarioId, Long deviceId) {
    featureGuard.requireEnabled();
    MobileCaptureDevice device = requireOwnedDevice(usuarioId, deviceId);
    device.setActive(false);
    device.setRevokedAt(LocalDateTime.now());
    deviceRepository.save(device);
  }

  @Transactional
  public MobileDeviceRegistrationResponse rotateToken(Long usuarioId, Long deviceId) {
    featureGuard.requireEnabled();
    MobileCaptureDevice device = requireOwnedDevice(usuarioId, deviceId);
    if (!device.isUsable()) {
      throw new IllegalStateException("Dispositivo revogado");
    }
    String rawToken = DeviceTokenHasher.generateToken();
    device.setTokenHash(DeviceTokenHasher.hashToken(rawToken));
    deviceRepository.save(device);
    return new MobileDeviceRegistrationResponse(
        device.getId(),
        rawToken,
        buildIngestionUrl(),
        device.getPlatform(),
        device.getName()
    );
  }

  public MobileCaptureDevice requireOwnedDevice(Long usuarioId, Long deviceId) {
    return deviceRepository.findByIdAndUsuarioId(deviceId, usuarioId)
        .orElseThrow(() -> new ResourceNotFoundException("Dispositivo não encontrado"));
  }

  private String buildIngestionUrl() {
    String base = properties.getIngestionBaseUrl();
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    return base + "/api/ingestion/mobile/transactions";
  }

  private MobileCaptureDeviceDto toDto(MobileCaptureDevice device) {
    return MobileCaptureDeviceDto.builder()
        .id(device.getId())
        .name(device.getName())
        .platform(device.getPlatform())
        .active(device.isActive() && device.isUsable())
        .createdAt(device.getCreatedAt())
        .lastSeenAt(device.getLastSeenAt())
        .revokedAt(device.getRevokedAt())
        .lastTestOkAt(device.getLastTestOkAt())
        .build();
  }
}
