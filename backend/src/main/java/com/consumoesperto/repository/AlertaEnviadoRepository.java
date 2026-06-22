package com.consumoesperto.repository;

import com.consumoesperto.model.AlertaEnviado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AlertaEnviadoRepository extends JpaRepository<AlertaEnviado, Long> {

    Optional<AlertaEnviado> findTopByUsuarioIdAndPeriodoOrderByDataEnvioDesc(Long usuarioId, String periodo);
}
