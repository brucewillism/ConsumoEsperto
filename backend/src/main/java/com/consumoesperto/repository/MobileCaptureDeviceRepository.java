package com.consumoesperto.repository;

import com.consumoesperto.model.MobileCaptureDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MobileCaptureDeviceRepository extends JpaRepository<MobileCaptureDevice, Long> {

    List<MobileCaptureDevice> findByUsuarioIdOrderByCreatedAtDesc(Long usuarioId);

    Optional<MobileCaptureDevice> findByIdAndUsuarioId(Long id, Long usuarioId);

    Optional<MobileCaptureDevice> findByTokenHashAndActiveTrue(String tokenHash);
}
