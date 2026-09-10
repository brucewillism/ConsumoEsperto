package com.consumoesperto.repository;

import com.consumoesperto.model.NotificacaoBancariaRecebida;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificacaoBancariaRecebidaRepository extends JpaRepository<NotificacaoBancariaRecebida, Long> {

    List<NotificacaoBancariaRecebida> findByUsuarioIdOrderByCriadoEmDesc(Long usuarioId, Pageable pageable);

    Optional<NotificacaoBancariaRecebida> findFirstByUsuarioIdAndIdExterno(Long usuarioId, String idExterno);

    List<NotificacaoBancariaRecebida> findByUsuarioIdAndHashDedupAndCriadoEmAfter(
        Long usuarioId, String hashDedup, LocalDateTime criadoEmAfter);

    List<NotificacaoBancariaRecebida> findByAvisoPendenteTrue();

    @Modifying
    @Query("DELETE FROM NotificacaoBancariaRecebida n WHERE n.criadoEm < :corte")
    int deleteByCriadoEmBefore(@Param("corte") LocalDateTime corte);
}
