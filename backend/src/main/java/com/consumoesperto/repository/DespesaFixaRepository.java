package com.consumoesperto.repository;

import com.consumoesperto.model.DespesaFixa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DespesaFixaRepository extends JpaRepository<DespesaFixa, Long> {

    List<DespesaFixa> findByUsuarioIdOrderByDiaVencimentoAscIdAsc(Long usuarioId);
}
