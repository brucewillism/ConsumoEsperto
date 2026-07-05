package com.consumoesperto.repository;

import com.consumoesperto.model.DespesaFixa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DespesaFixaRepository extends JpaRepository<DespesaFixa, Long> {

    List<DespesaFixa> findByUsuarioIdOrderByDiaVencimentoAscIdAsc(Long usuarioId);

    /** Despesas com débito automático ligado, com usuário e conta carregados (uso em job fora de sessão). */
    @Query("SELECT d FROM DespesaFixa d JOIN FETCH d.usuario LEFT JOIN FETCH d.contaBancaria WHERE d.debitoAutomatico = true")
    List<DespesaFixa> findAllComDebitoAutomatico();
}
