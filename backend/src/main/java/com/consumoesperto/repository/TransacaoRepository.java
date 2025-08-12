package com.consumoesperto.repository;

import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Transacao.TipoTransacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransacaoRepository extends JpaRepository<Transacao, Long> {

    List<Transacao> findByUsuarioIdOrderByDataTransacaoDesc(Long usuarioId);
    
    List<Transacao> findByUsuarioIdAndTipoTransacaoOrderByDataTransacaoDesc(Long usuarioId, TipoTransacao tipoTransacao);
    
    List<Transacao> findByUsuarioIdAndCategoriaIdOrderByDataTransacaoDesc(Long usuarioId, Long categoriaId);
    
    @Query("SELECT SUM(t.valor) FROM Transacao t WHERE t.usuario.id = :usuarioId AND t.tipoTransacao = :tipoTransacao")
    BigDecimal sumValorByUsuarioIdAndTipoTransacao(@Param("usuarioId") Long usuarioId, @Param("tipoTransacao") TipoTransacao tipoTransacao);
    
    @Query("SELECT SUM(t.valor) FROM Transacao t WHERE t.usuario.id = :usuarioId AND t.dataTransacao BETWEEN :dataInicio AND :dataFim")
    BigDecimal sumValorByUsuarioIdAndPeriodo(@Param("usuarioId") Long usuarioId, 
                                            @Param("dataInicio") LocalDateTime dataInicio, 
                                            @Param("dataFim") LocalDateTime dataFim);
    
    List<Transacao> findByUsuarioIdAndDataTransacaoBetweenOrderByDataTransacaoDesc(Long usuarioId, LocalDateTime dataInicio, LocalDateTime dataFim);
    
    @Query("SELECT SUM(t.valor) FROM Transacao t WHERE t.usuario.id = :usuarioId AND t.tipoTransacao = :tipoTransacao AND t.dataTransacao BETWEEN :dataInicio AND :dataFim")
    BigDecimal sumByUsuarioIdAndTipoAndPeriodo(@Param("usuarioId") Long usuarioId, 
                                              @Param("tipoTransacao") TipoTransacao tipoTransacao,
                                              @Param("dataInicio") LocalDateTime dataInicio, 
                                              @Param("dataFim") LocalDateTime dataFim);
    
    @Query("SELECT t.categoria.nome, SUM(t.valor) FROM Transacao t WHERE t.usuario.id = :usuarioId AND t.dataTransacao BETWEEN :dataInicio AND :dataFim GROUP BY t.categoria.nome")
    List<Object[]> findByUsuarioIdAndPeriodoGroupByCategoria(@Param("usuarioId") Long usuarioId,
                                                            @Param("dataInicio") LocalDateTime dataInicio,
                                                            @Param("dataFim") LocalDateTime dataFim);
}
