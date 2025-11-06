package com.consumoesperto.repository;

import com.consumoesperto.model.Notificacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository para gerenciar notificações
 * TEMPORARIAMENTE DESABILITADO PARA DEBUG
 */
@Repository
public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    /**
     * Busca notificações não lidas de um usuário
     */
    @Query("SELECT n FROM Notificacao n WHERE n.usuarioId = :usuarioId AND n.lida = false ORDER BY n.dataCriacao DESC")
    List<Notificacao> findByUsuarioIdAndLidaFalse(@Param("usuarioId") Long usuarioId);

    /**
     * Busca todas as notificações de um usuário ordenadas por data de criação
     */
    @Query("SELECT n FROM Notificacao n WHERE n.usuarioId = :usuarioId ORDER BY n.dataCriacao DESC")
    List<Notificacao> findByUsuarioIdOrderByDataCriacaoDesc(@Param("usuarioId") Long usuarioId);

    /**
     * Busca notificações por tipo
     */
    @Query("SELECT n FROM Notificacao n WHERE n.usuarioId = :usuarioId AND n.tipo = :tipo ORDER BY n.dataCriacao DESC")
    List<Notificacao> findByUsuarioIdAndTipo(@Param("usuarioId") Long usuarioId, @Param("tipo") String tipo);

    /**
     * Conta notificações não lidas de um usuário
     */
    @Query("SELECT COUNT(n) FROM Notificacao n WHERE n.usuarioId = :usuarioId AND n.lida = false")
    Long countByUsuarioIdAndLidaFalse(@Param("usuarioId") Long usuarioId);
}
