package com.consumoesperto.mobilecapture.service;

import com.consumoesperto.exception.ResourceNotFoundException;
import com.consumoesperto.mobilecapture.dto.ConfirmMobileCaptureEventRequest;
import com.consumoesperto.mobilecapture.dto.CreateMobileSourceMappingRequest;
import com.consumoesperto.mobilecapture.dto.MobileSourceMappingDto;
import com.consumoesperto.model.MobileCaptureDevice;
import com.consumoesperto.model.MobileSourceMapping;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.ContaBancariaRepository;
import com.consumoesperto.repository.MobileSourceMappingRepository;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MobileSourceMappingService {

  private final MobileCaptureFeatureGuard featureGuard;
  private final MobileSourceMappingRepository mappingRepository;
  private final MobileCaptureDeviceService deviceService;
  private final UsuarioRepository usuarioRepository;
  private final ContaBancariaRepository contaBancariaRepository;
  private final CartaoCreditoRepository cartaoCreditoRepository;

  public List<MobileSourceMappingDto> list(Long usuarioId) {
    featureGuard.requireEnabled();
    return mappingRepository.findByUsuarioIdAndEnabledTrueOrderByUpdatedAtDesc(usuarioId).stream()
        .map(this::toDto)
        .toList();
  }

  @Transactional
  public MobileSourceMappingDto create(Long usuarioId, CreateMobileSourceMappingRequest request) {
    featureGuard.requireEnabled();
    Usuario usuario = usuarioRepository.findById(usuarioId)
        .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    validateOwnership(usuarioId, request.getContaId(), request.getCartaoId());
    MobileSourceMapping mapping = new MobileSourceMapping();
    mapping.setUsuario(usuario);
    if (request.getDeviceId() != null) {
      MobileCaptureDevice device = deviceService.requireOwnedDevice(usuarioId, request.getDeviceId());
      mapping.setDevice(device);
    }
    mapping.setPackageName(blankToNull(request.getPackageName()));
    mapping.setProviderKey(blankToNull(request.getProviderKey()));
    mapping.setCardLast4(blankToNull(request.getCardLast4()));
    mapping.setContaId(request.getContaId());
    mapping.setCartaoId(request.getCartaoId());
    mapping.setEnabled(true);
    mapping.setUpdatedAt(LocalDateTime.now());
    return toDto(mappingRepository.save(mapping));
  }

  @Transactional
  public void delete(Long usuarioId, Long mappingId) {
    featureGuard.requireEnabled();
    MobileSourceMapping mapping = mappingRepository.findById(mappingId)
        .filter(m -> m.getUsuario().getId().equals(usuarioId))
        .orElseThrow(() -> new ResourceNotFoundException("Mapeamento não encontrado"));
    mapping.setEnabled(false);
    mapping.setUpdatedAt(LocalDateTime.now());
    mappingRepository.save(mapping);
  }

  private void validateOwnership(Long usuarioId, Long contaId, Long cartaoId) {
    if (contaId != null && contaBancariaRepository.findByIdAndUsuarioId(contaId, usuarioId).isEmpty()) {
      throw new IllegalArgumentException("Conta inválida");
    }
    if (cartaoId != null && cartaoCreditoRepository.findByIdAndUsuarioId(cartaoId, usuarioId).isEmpty()) {
      throw new IllegalArgumentException("Cartão inválido");
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private MobileSourceMappingDto toDto(MobileSourceMapping mapping) {
    return MobileSourceMappingDto.builder()
        .id(mapping.getId())
        .deviceId(mapping.getDevice() != null ? mapping.getDevice().getId() : null)
        .packageName(mapping.getPackageName())
        .providerKey(mapping.getProviderKey())
        .cardLast4(mapping.getCardLast4())
        .contaId(mapping.getContaId())
        .cartaoId(mapping.getCartaoId())
        .enabled(mapping.isEnabled())
        .build();
  }
}
