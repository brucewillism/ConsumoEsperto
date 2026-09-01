package com.consumoesperto.repository;

import com.consumoesperto.model.MobileSourceMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MobileSourceMappingRepository extends JpaRepository<MobileSourceMapping, Long> {

    List<MobileSourceMapping> findByUsuarioIdAndEnabledTrueOrderByUpdatedAtDesc(Long usuarioId);

    List<MobileSourceMapping> findByUsuarioIdAndPackageNameAndEnabledTrue(
        Long usuarioId, String packageName);
}
