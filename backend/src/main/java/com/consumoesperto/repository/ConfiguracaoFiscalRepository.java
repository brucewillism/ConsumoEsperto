package com.consumoesperto.repository;

import com.consumoesperto.model.ConfiguracaoFiscal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConfiguracaoFiscalRepository extends JpaRepository<ConfiguracaoFiscal, Long> {

    Optional<ConfiguracaoFiscal> findByUsuarioId(Long usuarioId);
}
