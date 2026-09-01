package com.consumoesperto.repository;

import com.consumoesperto.model.IngestionEventStatus;
import com.consumoesperto.model.MobileCaptureEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MobileCaptureEventRepository extends JpaRepository<MobileCaptureEvent, Long> {

    Optional<MobileCaptureEvent> findByDeviceIdAndClientEventId(Long deviceId, String clientEventId);

    Optional<MobileCaptureEvent> findByUsuarioIdAndFingerprint(Long usuarioId, String fingerprint);

    List<MobileCaptureEvent> findByUsuarioIdAndStatusOrderByReceivedAtDesc(
        Long usuarioId, IngestionEventStatus status);

    Optional<MobileCaptureEvent> findByIdAndUsuarioId(Long id, Long usuarioId);
}
