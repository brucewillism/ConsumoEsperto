package com.consumoesperto.repository;

import com.consumoesperto.model.NotificacaoFechamentoCartao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface NotificacaoFechamentoCartaoRepository extends JpaRepository<NotificacaoFechamentoCartao, Long> {

    boolean existsByCartaoCreditoIdAndDataFechamentoReferencia(Long cartaoCreditoId, LocalDate dataFechamentoReferencia);
}
