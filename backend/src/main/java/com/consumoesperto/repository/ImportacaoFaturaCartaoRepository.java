package com.consumoesperto.repository;

import com.consumoesperto.model.ImportacaoFaturaCartao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface ImportacaoFaturaCartaoRepository extends JpaRepository<ImportacaoFaturaCartao, Long> {
    List<ImportacaoFaturaCartao> findByUsuarioIdAndStatusOrderByDataCriacaoDesc(Long usuarioId, ImportacaoFaturaCartao.Status status);
    Optional<ImportacaoFaturaCartao> findByIdAndUsuarioId(Long id, Long usuarioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM ImportacaoFaturaCartao i WHERE i.id = :id AND i.usuario.id = :usuarioId")
    Optional<ImportacaoFaturaCartao> findByIdAndUsuarioIdForUpdate(@Param("id") Long id, @Param("usuarioId") Long usuarioId);
    List<ImportacaoFaturaCartao> findByUsuarioIdAndCartaoCreditoIdOrderByDataVencimentoDesc(Long usuarioId, Long cartaoCreditoId);

    List<ImportacaoFaturaCartao> findByUsuarioId(Long usuarioId);
}
