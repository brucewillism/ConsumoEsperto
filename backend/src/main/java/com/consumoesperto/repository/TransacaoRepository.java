package com.consumoesperto.repository;

import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Transacao.TipoTransacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransacaoRepository extends JpaRepository<Transacao, Long> {

    List<Transacao> findByUsuarioIdOrderByDataTransacaoDesc(Long usuarioId);
    
    List<Transacao> findByUsuarioIdAndTipoTransacaoOrderByDataTransacaoDesc(Long usuarioId, TipoTransacao tipoTransacao);
    
    List<Transacao> findByUsuarioIdAndCategoriaIdOrderByDataTransacaoDesc(Long usuarioId, Long categoriaId);
    
    @Query("SELECT SUM(t.valor) FROM Transacao t WHERE t.usuario.id = :usuarioId AND t.tipoTransacao = :tipoTransacao")
    BigDecimal sumValorByUsuarioIdAndTipoTransacao(@Param("usuarioId") Long usuarioId, @Param("tipoTransacao") TipoTransacao tipoTransacao);

    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t WHERE t.usuario.id = :usuarioId "
        + "AND t.tipoTransacao = :tipoTransacao "
        + "AND t.statusConferencia = com.consumoesperto.model.Transacao$StatusConferencia.CONFIRMADA")
    BigDecimal sumValorConfirmadaByUsuarioIdAndTipoTransacao(
        @Param("usuarioId") Long usuarioId,
        @Param("tipoTransacao") TipoTransacao tipoTransacao
    );
    
    @Query("SELECT SUM(t.valor) FROM Transacao t WHERE t.usuario.id = :usuarioId AND t.dataTransacao BETWEEN :dataInicio AND :dataFim")
    BigDecimal sumValorByUsuarioIdAndPeriodo(@Param("usuarioId") Long usuarioId, 
                                            @Param("dataInicio") LocalDateTime dataInicio, 
                                            @Param("dataFim") LocalDateTime dataFim);
    
    List<Transacao> findByUsuarioIdAndDataTransacaoBetweenOrderByDataTransacaoDesc(Long usuarioId, LocalDateTime dataInicio, LocalDateTime dataFim);
    
    // Método de compatibilidade para código existente
    List<Transacao> findByUsuarioIdAndDataTransacaoBetween(Long usuarioId, LocalDateTime dataInicio, LocalDateTime dataFim);
    
    @Query("SELECT SUM(t.valor) FROM Transacao t WHERE t.usuario.id = :usuarioId AND t.tipoTransacao = :tipoTransacao AND t.dataTransacao BETWEEN :dataInicio AND :dataFim")
    BigDecimal sumByUsuarioIdAndTipoAndPeriodo(@Param("usuarioId") Long usuarioId, 
                                              @Param("tipoTransacao") TipoTransacao tipoTransacao,
                                              @Param("dataInicio") LocalDateTime dataInicio, 
                                              @Param("dataFim") LocalDateTime dataFim);

    @Query("SELECT SUM(t.valor) FROM Transacao t WHERE t.usuario.id = :usuarioId AND t.tipoTransacao = :tipoTransacao AND t.statusConferencia = com.consumoesperto.model.Transacao$StatusConferencia.CONFIRMADA AND t.dataTransacao BETWEEN :dataInicio AND :dataFim")
    BigDecimal sumConfirmadaByUsuarioIdAndTipoAndPeriodo(@Param("usuarioId") Long usuarioId,
                                                         @Param("tipoTransacao") TipoTransacao tipoTransacao,
                                                         @Param("dataInicio") LocalDateTime dataInicio,
                                                         @Param("dataFim") LocalDateTime dataFim);
    
    @Query("SELECT t.categoria.nome, SUM(t.valor) FROM Transacao t WHERE t.usuario.id = :usuarioId AND t.dataTransacao BETWEEN :dataInicio AND :dataFim GROUP BY t.categoria.nome")
    List<Object[]> findByUsuarioIdAndPeriodoGroupByCategoria(@Param("usuarioId") Long usuarioId,
                                                            @Param("dataInicio") LocalDateTime dataInicio,
                                                            @Param("dataFim") LocalDateTime dataFim);

    @Query("SELECT t.categoria.nome, SUM(t.valor) FROM Transacao t WHERE t.usuario.id = :usuarioId AND t.statusConferencia = com.consumoesperto.model.Transacao$StatusConferencia.CONFIRMADA AND t.dataTransacao BETWEEN :dataInicio AND :dataFim GROUP BY t.categoria.nome")
    List<Object[]> findConfirmadasByUsuarioIdAndPeriodoGroupByCategoria(@Param("usuarioId") Long usuarioId,
                                                                        @Param("dataInicio") LocalDateTime dataInicio,
                                                                        @Param("dataFim") LocalDateTime dataFim);

    @Query("SELECT COALESCE(t.categoria.nome, 'Sem categoria'), SUM(t.valor) " +
           "FROM Transacao t " +
           "WHERE t.usuario.id = :usuarioId " +
           "AND t.tipoTransacao = com.consumoesperto.model.Transacao$TipoTransacao.DESPESA " +
           "AND t.statusConferencia = com.consumoesperto.model.Transacao$StatusConferencia.CONFIRMADA " +
           "AND t.dataTransacao BETWEEN :dataInicio AND :dataFim " +
           "GROUP BY t.categoria.nome " +
           "ORDER BY SUM(t.valor) DESC")
    List<Object[]> findDespesasByUsuarioIdAndPeriodoGroupByCategoria(@Param("usuarioId") Long usuarioId,
                                                                      @Param("dataInicio") LocalDateTime dataInicio,
                                                                      @Param("dataFim") LocalDateTime dataFim);
    
    List<Transacao> findByUsuarioIdAndDescricaoAndDataTransacaoAndValor(Long usuarioId, String descricao, LocalDateTime dataTransacao, BigDecimal valor);
    
    List<Transacao> findByUsuarioIdAndDescricaoContaining(Long usuarioId, String descricao);

    List<Transacao> findByUsuarioIdAndRecorrenteIsTrueAndTipoTransacao(Long usuarioId, TipoTransacao tipoTransacao);

    List<Transacao> findByRecorrenteTrueAndProximaExecucaoLessThanEqual(LocalDate dataReferencia);
    
    int deleteByUsuarioId(Long usuarioId);

    @EntityGraph(attributePaths = "categoria")
    @Query("SELECT t FROM Transacao t WHERE t.usuario.id = :usuarioId AND t.tipoTransacao = :tipo " +
        "AND t.statusConferencia = com.consumoesperto.model.Transacao$StatusConferencia.CONFIRMADA " +
        "AND t.dataTransacao >= :inicio AND t.dataTransacao <= :fim ORDER BY t.id")
    Page<Transacao> findPagedForIrExport(
        @Param("usuarioId") Long usuarioId,
        @Param("tipo") TipoTransacao tipo,
        @Param("inicio") LocalDateTime inicio,
        @Param("fim") LocalDateTime fim,
        Pageable pageable
    );

    @Query("SELECT t FROM Transacao t JOIN FETCH t.usuario u WHERE " +
        "t.statusConferencia = com.consumoesperto.model.Transacao$StatusConferencia.PENDENTE " +
        "AND t.dataCriacao < :limite AND " +
        "NOT EXISTS (SELECT 1 FROM WhatsAppLembretePendencia w WHERE w.transacaoId = t.id AND w.tipo = :tipoLembrete)")
    List<Transacao> findPendentesParaLembrete(
        @Param("limite") LocalDateTime limite,
        @Param("tipoLembrete") String tipoLembrete
    );

    @Query("SELECT t FROM Transacao t WHERE t.usuario.id = :usuarioId "
        + "AND t.tipoTransacao = com.consumoesperto.model.Transacao$TipoTransacao.DESPESA "
        + "AND t.statusConferencia = com.consumoesperto.model.Transacao$StatusConferencia.CONFIRMADA "
        + "AND t.dataTransacao >= :inicio ORDER BY t.dataTransacao DESC")
    List<Transacao> findDespesasConfirmadasDesde(
        @Param("usuarioId") Long usuarioId,
        @Param("inicio") LocalDateTime inicio
    );

    @Query("SELECT COUNT(t) FROM Transacao t WHERE t.usuario.id = :usuarioId "
        + "AND t.statusConferencia = com.consumoesperto.model.Transacao$StatusConferencia.CONFIRMADA "
        + "AND t.dataTransacao >= :inicio AND t.dataTransacao <= :fim")
    long countConfirmadasNoPeriodo(
        @Param("usuarioId") Long usuarioId,
        @Param("inicio") LocalDateTime inicio,
        @Param("fim") LocalDateTime fim
    );

    /** Todas as transações do usuário no intervalo (inclui sem categoria/cartão; respeita @Where excluido=false). */
    @Query("SELECT COUNT(t) FROM Transacao t WHERE t.usuario.id = :usuarioId "
        + "AND t.dataTransacao >= :inicio AND t.dataTransacao <= :fim")
    long countTransacoesUsuarioNoPeriodo(
        @Param("usuarioId") Long usuarioId,
        @Param("inicio") LocalDateTime inicio,
        @Param("fim") LocalDateTime fim
    );

    @Query("SELECT t FROM Transacao t WHERE t.usuario.id = :usuarioId AND t.excluido = false "
        + "AND LOWER(t.descricao) LIKE LOWER(CONCAT('%', :termo, '%')) "
        + "AND t.tipoTransacao = :tipo "
        + "AND t.dataTransacao >= :inicio AND t.dataTransacao <= :fim "
        + "ORDER BY t.dataTransacao ASC, t.id ASC")
    List<Transacao> searchByUsuarioDescricaoNoMes(
        @Param("usuarioId") Long usuarioId,
        @Param("termo") String termo,
        @Param("tipo") TipoTransacao tipo,
        @Param("inicio") LocalDateTime inicio,
        @Param("fim") LocalDateTime fim
    );

    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t WHERE t.fatura.id = :faturaId "
        + "AND t.tipoTransacao = com.consumoesperto.model.Transacao$TipoTransacao.DESPESA "
        + "AND t.statusConferencia = com.consumoesperto.model.Transacao$StatusConferencia.CONFIRMADA")
    BigDecimal sumDespesaConfirmadaPorFaturaId(@Param("faturaId") Long faturaId);

    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t WHERE t.fatura.cartaoCredito.id = :cartaoId "
        + "AND t.fatura.status IN (com.consumoesperto.model.Fatura$StatusFatura.ABERTA, com.consumoesperto.model.Fatura$StatusFatura.PARCIAL) "
        + "AND t.tipoTransacao = com.consumoesperto.model.Transacao$TipoTransacao.DESPESA "
        + "AND t.statusConferencia = com.consumoesperto.model.Transacao$StatusConferencia.CONFIRMADA")
    BigDecimal sumDespesaConfirmadaFaturaAbertaPorCartaoId(@Param("cartaoId") Long cartaoId);

    /** Soma despesas confirmadas em faturas ainda não quitadas (inclui PREVISTA e parcelas futuras). */
    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t WHERE t.fatura.cartaoCredito.id = :cartaoId "
        + "AND t.tipoTransacao = com.consumoesperto.model.Transacao$TipoTransacao.DESPESA "
        + "AND t.statusConferencia = com.consumoesperto.model.Transacao$StatusConferencia.CONFIRMADA "
        + "AND t.fatura.status NOT IN (com.consumoesperto.model.Fatura$StatusFatura.PAGA, com.consumoesperto.model.Fatura$StatusFatura.CANCELADA)")
    BigDecimal sumDespesaConfirmadaFaturasNaoPagasPorCartaoId(@Param("cartaoId") Long cartaoId);

    List<Transacao> findByUsuarioIdAndGrupoParcelaIdOrderByParcelaAtualAsc(Long usuarioId, String grupoParcelaId);

    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t WHERE t.usuario.id = :usuarioId "
        + "AND t.tipoTransacao = com.consumoesperto.model.Transacao$TipoTransacao.DESPESA "
        + "AND t.statusConferencia = com.consumoesperto.model.Transacao$StatusConferencia.CONFIRMADA "
        + "AND t.fatura IS NOT NULL "
        + "AND t.fatura.status NOT IN (com.consumoesperto.model.Fatura$StatusFatura.PAGA, com.consumoesperto.model.Fatura$StatusFatura.CANCELADA) "
        + "AND t.fatura.dataVencimento > :limite")
    BigDecimal sumParcelasFuturasConfirmadasApos(@Param("usuarioId") Long usuarioId, @Param("limite") java.time.LocalDateTime limite);

    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t WHERE t.usuario.id = :usuarioId "
        + "AND t.tipoTransacao = com.consumoesperto.model.Transacao$TipoTransacao.DESPESA "
        + "AND t.statusConferencia = com.consumoesperto.model.Transacao$StatusConferencia.CONFIRMADA "
        + "AND t.fatura IS NULL")
    BigDecimal sumDespesaConfirmadaCaixaPorUsuarioId(@Param("usuarioId") Long usuarioId);

    @Query("SELECT t FROM Transacao t WHERE t.usuario.id = :usuarioId AND t.tipoTransacao = :tipo "
        + "AND t.dataTransacao >= :inicio AND t.dataTransacao <= :fim ORDER BY t.dataTransacao DESC, t.id DESC")
    List<Transacao> findByUsuarioIdAndTipoAndPeriodo(
        @Param("usuarioId") Long usuarioId,
        @Param("tipo") TipoTransacao tipo,
        @Param("inicio") LocalDateTime inicio,
        @Param("fim") LocalDateTime fim
    );
}
