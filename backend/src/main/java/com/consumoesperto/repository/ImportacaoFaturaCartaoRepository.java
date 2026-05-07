package com.consumoesperto.repository;

import com.consumoesperto.model.ImportacaoFaturaCartao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ImportacaoFaturaCartaoRepository extends JpaRepository<ImportacaoFaturaCartao, Long> {
    List<ImportacaoFaturaCartao> findByUsuarioIdAndStatusOrderByDataCriacaoDesc(Long usuarioId, ImportacaoFaturaCartao.Status status);
    Optional<ImportacaoFaturaCartao> findByIdAndUsuarioId(Long id, Long usuarioId);
    List<ImportacaoFaturaCartao> findByUsuarioIdAndCartaoCreditoIdOrderByDataVencimentoDesc(Long usuarioId, Long cartaoCreditoId);
}
