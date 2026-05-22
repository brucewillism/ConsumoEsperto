package com.consumoesperto.repository;

import com.consumoesperto.model.TransferenciaConta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransferenciaContaRepository extends JpaRepository<TransferenciaConta, Long> {

    List<TransferenciaConta> findByUsuarioIdOrderByDataTransferenciaDesc(Long usuarioId);
}
