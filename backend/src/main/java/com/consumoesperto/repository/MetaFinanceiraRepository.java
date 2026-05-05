package com.consumoesperto.repository;

import com.consumoesperto.model.MetaFinanceira;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MetaFinanceiraRepository extends JpaRepository<MetaFinanceira, Long> {

    List<MetaFinanceira> findByUsuarioIdOrderByPrioridadeDescDataCriacaoDesc(Long usuarioId);

    Optional<MetaFinanceira> findByIdAndUsuarioId(Long id, Long usuarioId);

    List<MetaFinanceira> findByUsuarioIdAndDescricaoContainingIgnoreCase(Long usuarioId, String fragmento);
}
