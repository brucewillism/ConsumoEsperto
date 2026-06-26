package com.consumoesperto.repository;

import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Transacao.TipoTransacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
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

    @Query("SELECT t FROM Transacao t WHERE t.usuario.id = :usuarioId " +
           "AND COALESCE(t.dataTransacao, t.dataCriacao) BETWEEN :dataInicio AND :dataFim " +
           "ORDER BY COALESCE(t.dataTransacao, t.dataCriacao) DESC")
    List<Transacao> findByUsuarioIdAndPeriodoEfetivoOrderByDataDesc(@Param("usuarioId") Long usuarioId,
                                                                     @Param("dataInicio") LocalDateTime dataInicio,
                                                                     @Param("dataFim") LocalDateTime dataFim);

    @EntityGraph(attributePaths = "categoria")
    @Query("SELECT t FROM Transacao t WHERE t.usuario.id = :usuarioId "
        + "AND COALESCE(t.dataTransacao, t.dataCriacao) BETWEEN :dataInicio AND :dataFim "
        + "ORDER BY COALESCE(t.dataTransacao, t.dataCriacao) DESC, t.id DESC")
    Page<Transacao> findPageByUsuarioIdAndPeriodoEfetivo(
        @Param("usuarioId") Long usuarioId,
        @Param("dataInicio") LocalDateTime dataInicio,
        @Param("dataFim") LocalDateTime dataFim,
        Pageable pageable);
    
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

    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t WHERE t.usuario.id = :usuarioId "
        + "AND t.tipoTransacao = com.consumoesperto.model.Transacao$TipoTransacao.RECEITA "
        + "AND t.statusConferencia = com.consumoesperto.model.Transacao$StatusConferencia.CONFIRMADA "
        + "AND t.dataTransacao BETWEEN :dataInicio AND :dataFim")
    BigDecimal sumReceitasConfirmadasPeriodo(
        @Param("usuarioId") Long usuarioId,
        @Param("dataInicio") LocalDateTime dataInicio,
        @Param("dataFim") LocalDateTime dataFim
    );
    
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

    /** Dashboard — todas as despesas do período (confirmadas ou pendentes), agrupadas por categoria. */
    @Query("SELECT COALESCE(t.categoria.nome, 'Sem categoria'), SUM(t.valor) " +
           "FROM Transacao t " +
           "WHERE t.usuario.id = :usuarioId " +
           "AND t.tipoTransacao = com.consumoesperto.model.Transacao$TipoTransacao.DESPESA " +
           "AND COALESCE(t.dataTransacao, t.dataCriacao) BETWEEN :dataInicio AND :dataFim " +
           "GROUP BY COALESCE(t.categoria.nome, 'Sem categoria') " +
           "ORDER BY SUM(t.valor) DESC")
    List<Object[]> findDespesasDashboardByUsuarioIdAndPeriodoGroupByCategoria(@Param("usuarioId") Long usuarioId,
                                                                               @Param("dataInicio") LocalDateTime dataInicio,
                                                                               @Param("dataFim") LocalDateTime dataFim);
    
    List<Transacao> findByUsuarioIdAndDescricaoAndDataTransacaoAndValor(Long usuarioId, String descricao, LocalDateTime dataTransacao, BigDecimal valor);
    
    List<Transacao> findByUsuarioIdAndDescricaoContaining(Long usuarioId, String descricao);

    List<Transacao> findByUsuarioIdAndRecorrenteIsTrueAndTipoTransacao(Long usuarioId, TipoTransacao tipoTransacao);

    List<Transacao> findByRecorrenteTrueAndProximaExecucaoLessThanEqual(LocalDate dataReferencia);
    
    int deleteByUsuarioId(Long usuarioId);

    /**
     * Despesas no ano-calendário para exportação IR. Usa a data efetiva do lançamento:
     * {@code COALESCE(dataTransacao, dataCriacao)} para não perder registos antigos sem data de movimento.
     */
    @EntityGraph(attributePaths = "categoria")
    @Query("SELECT t FROM Transacao t WHERE t.usuario.id = :usuarioId AND t.tipoTransacao = :tipo "
        + "AND t.statusConferencia = :status "
        + "AND COALESCE(t.dataTransacao, t.dataCriacao) >= :inicio "
        + "AND COALESCE(t.dataTransacao, t.dataCriacao) <= :fim ORDER BY t.id")
    Page<Transacao> findPagedForIrExport(
        @Param("usuarioId") Long usuarioId,
        @Param("tipo") TipoTransacao tipo,
        @Param("status") Transacao.StatusConferencia status,
        @Param("inicio") LocalDateTime inicio,
        @Param("fim") LocalDateTime fim,
        Pageable pageable
    );

    @EntityGraph(attributePaths = "categoria")
    @Query("SELECT t FROM Transacao t WHERE t.usuario.id = :usuarioId AND t.tipoTransacao = :tipo "
        + "AND t.statusConferencia IN :statuses "
        + "AND COALESCE(t.dataTransacao, t.dataCriacao) >= :inicio "
        + "AND COALESCE(t.dataTransacao, t.dataCriacao) <= :fim "
        + "ORDER BY COALESCE(t.dataTransacao, t.dataCriacao) DESC, t.id DESC")
    Page<Transacao> findPagedForIrDetalhe(
        @Param("usuarioId") Long usuarioId,
        @Param("tipo") TipoTransacao tipo,
        @Param("statuses") Collection<Transacao.StatusConferencia> statuses,
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

    /** Despesas em conta (sem fatura de cartão) — base para burn rate diário. */
    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t WHERE t.usuario.id = :usuarioId "
        + "AND t.tipoTransacao = com.consumoesperto.model.Transacao$TipoTransacao.DESPESA "
        + "AND t.statusConferencia = com.consumoesperto.model.Transacao$StatusConferencia.CONFIRMADA "
        + "AND t.fatura IS NULL "
        + "AND t.dataTransacao >= :inicio AND t.dataTransacao < :fim")
    BigDecimal sumDespesaContaCorrenteConfirmadaPeriodo(
        @Param("usuarioId") Long usuarioId,
        @Param("inicio") LocalDateTime inicio,
        @Param("fim") LocalDateTime fim
    );

    /** Receitas + despesas/investimentos confirmados em conta (sem fatura) — delta líquido no período. */
    @Query("SELECT COALESCE(SUM(CASE WHEN t.tipoTransacao = com.consumoesperto.model.Transacao$TipoTransacao.RECEITA "
        + "THEN t.valor ELSE -t.valor END), 0) FROM Transacao t WHERE t.usuario.id = :usuarioId "
        + "AND t.statusConferencia = com.consumoesperto.model.Transacao$StatusConferencia.CONFIRMADA "
        + "AND t.fatura IS NULL "
        + "AND t.tipoTransacao IN (com.consumoesperto.model.Transacao$TipoTransacao.RECEITA, "
        + "com.consumoesperto.model.Transacao$TipoTransacao.DESPESA, com.consumoesperto.model.Transacao$TipoTransacao.INVESTIMENTO) "
        + "AND t.dataTransacao >= :inicio AND t.dataTransacao <= :fim")
    BigDecimal sumMovimentoLiquidoContaConfirmadaPeriodo(
        @Param("usuarioId") Long usuarioId,
        @Param("inicio") LocalDateTime inicio,
        @Param("fim") LocalDateTime fim
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

    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t WHERE t.fatura.id = :faturaId "
        + "AND t.tipoTransacao = com.consumoesperto.model.Transacao$TipoTransacao.PAGAMENTO_FATURA "
        + "AND t.statusConferencia = com.consumoesperto.model.Transacao$StatusConferencia.CONFIRMADA")
    BigDecimal sumPagamentoFaturaConfirmadoPorFaturaId(@Param("faturaId") Long faturaId);

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

    List<Transacao> findByFaturaIdOrderByDataTransacaoAscIdAsc(Long faturaId);

    @Query("SELECT t FROM Transacao t LEFT JOIN FETCH t.contaBancaria WHERE t.fatura.id = :faturaId "
        + "ORDER BY t.dataTransacao ASC, t.id ASC")
    List<Transacao> findByFaturaIdWithContaOrderByDataTransacaoAscIdAsc(@Param("faturaId") Long faturaId);

    @Query("SELECT t FROM Transacao t JOIN FETCH t.contaBancaria "
        + "WHERE t.fatura.id = :faturaId "
        + "AND t.tipoTransacao = com.consumoesperto.model.Transacao$TipoTransacao.PAGAMENTO_FATURA "
        + "AND t.statusConferencia = com.consumoesperto.model.Transacao$StatusConferencia.CONFIRMADA "
        + "ORDER BY t.dataTransacao ASC, t.id ASC")
    List<Transacao> findPagamentosFaturaConfirmadosComContaPorFaturaId(@Param("faturaId") Long faturaId);

    @Query(
        value = "SELECT t.conta_bancaria_id, t.valor FROM transacoes t "
            + "WHERE t.fatura_id = :faturaId "
            + "AND t.tipo_transacao = 'PAGAMENTO_FATURA' "
            + "AND t.status_conferencia = 'CONFIRMADA' "
            + "AND t.conta_bancaria_id IS NOT NULL "
            + "AND t.excluido = false "
            + "ORDER BY t.data_transacao ASC, t.id ASC",
        nativeQuery = true
    )
    List<Object[]> findPagamentosNativosEstornoPorFaturaId(@Param("faturaId") Long faturaId);

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

    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t WHERE t.usuario.id = :usuarioId "
        + "AND t.categoria.id = :categoriaId "
        + "AND t.tipoTransacao = com.consumoesperto.model.Transacao$TipoTransacao.DESPESA "
        + "AND t.statusConferencia = com.consumoesperto.model.Transacao$StatusConferencia.CONFIRMADA "
        + "AND t.dataTransacao >= :inicio AND t.dataTransacao <= :fim")
    BigDecimal sumDespesaConfirmadaPorCategoriaNoPeriodo(
        @Param("usuarioId") Long usuarioId,
        @Param("categoriaId") Long categoriaId,
        @Param("inicio") LocalDateTime inicio,
        @Param("fim") LocalDateTime fim
    );

    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t WHERE t.usuario.id IN :usuarioIds "
        + "AND t.tipoTransacao = com.consumoesperto.model.Transacao$TipoTransacao.DESPESA "
        + "AND t.statusConferencia = com.consumoesperto.model.Transacao$StatusConferencia.CONFIRMADA "
        + "AND t.categoria IS NOT NULL "
        + "AND LOWER(t.categoria.nome) = LOWER(:categoriaNome) "
        + "AND t.dataTransacao >= :inicio AND t.dataTransacao <= :fim")
    BigDecimal sumDespesaConfirmadaPorUsuariosENomeCategoriaNoPeriodo(
        @Param("usuarioIds") List<Long> usuarioIds,
        @Param("categoriaNome") String categoriaNome,
        @Param("inicio") LocalDateTime inicio,
        @Param("fim") LocalDateTime fim
    );

    @Query("SELECT t FROM Transacao t JOIN FETCH t.usuario u JOIN FETCH t.categoria c WHERE t.usuario.id IN :usuarioIds "
        + "AND t.tipoTransacao = com.consumoesperto.model.Transacao$TipoTransacao.DESPESA "
        + "AND t.statusConferencia = com.consumoesperto.model.Transacao$StatusConferencia.CONFIRMADA "
        + "AND t.dataTransacao >= :inicio AND t.dataTransacao <= :fim")
    List<Transacao> findDespesasConfirmadasPorUsuariosNoPeriodoComCategoria(
        @Param("usuarioIds") List<Long> usuarioIds,
        @Param("inicio") LocalDateTime inicio,
        @Param("fim") LocalDateTime fim
    );

    @Query("SELECT t FROM Transacao t WHERE t.usuario.id = :usuarioId AND t.tipoTransacao = :tipo "
        + "AND t.dataTransacao >= :inicio AND t.dataTransacao <= :fim ORDER BY t.dataTransacao DESC, t.id DESC")
    List<Transacao> findByUsuarioIdAndTipoAndPeriodo(
        @Param("usuarioId") Long usuarioId,
        @Param("tipo") TipoTransacao tipo,
        @Param("inicio") LocalDateTime inicio,
        @Param("fim") LocalDateTime fim
    );

    @Query("SELECT t FROM Transacao t JOIN FETCH t.usuario u LEFT JOIN FETCH t.categoria c WHERE "
        + "t.tipoTransacao = com.consumoesperto.model.Transacao$TipoTransacao.DESPESA "
        + "AND t.statusConferencia = com.consumoesperto.model.Transacao$StatusConferencia.CONFIRMADA "
        + "AND t.dataTransacao >= :inicio AND t.dataTransacao <= :fim ORDER BY t.usuario.id, t.dataTransacao ASC")
    List<Transacao> findDespesasConfirmadasNoPeriodoComUsuarioCategoria(
        @Param("inicio") LocalDateTime inicio,
        @Param("fim") LocalDateTime fim
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Transacao t SET t.excluido = true WHERE t.usuario.id = :usuarioId "
        + "AND t.statusConferencia = com.consumoesperto.model.Transacao$StatusConferencia.PREVISTO "
        + "AND t.origemFiscal IS NOT NULL AND t.excluido = false")
    int softDeleteProvisionamentosFiscaisPrevistos(@Param("usuarioId") Long usuarioId);

    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t WHERE t.usuario.id = :usuarioId "
        + "AND t.tipoTransacao = com.consumoesperto.model.Transacao$TipoTransacao.RECEITA "
        + "AND t.statusConferencia = com.consumoesperto.model.Transacao$StatusConferencia.PREVISTO "
        + "AND t.origemFiscal IS NOT NULL "
        + "AND t.dataTransacao BETWEEN :inicio AND :fim")
    BigDecimal sumReceitaFiscalPrevistaPeriodo(
        @Param("usuarioId") Long usuarioId,
        @Param("inicio") LocalDateTime inicio,
        @Param("fim") LocalDateTime fim
    );

    /**
     * Receitas salariais confirmadas no período — categoria Salário, recorrentes ou holerite automático;
     * exclui 13º/IR ({@code origemFiscal}) e entradas avulsas sem vínculo salarial.
     */
    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t WHERE t.usuario.id = :usuarioId "
        + "AND t.tipoTransacao = com.consumoesperto.model.Transacao$TipoTransacao.RECEITA "
        + "AND t.statusConferencia = com.consumoesperto.model.Transacao$StatusConferencia.CONFIRMADA "
        + "AND t.origemFiscal IS NULL AND t.excluido = false "
        + "AND t.dataTransacao BETWEEN :inicio AND :fim "
        + "AND (t.recorrente = true "
        + "OR LOWER(COALESCE(t.categoria.nome, '')) IN ('salário', 'salario') "
        + "OR LOWER(COALESCE(t.descricao, '')) LIKE '%salário líquido%' "
        + "OR LOWER(COALESCE(t.descricao, '')) LIKE '%salario liquido%')")
    BigDecimal sumReceitaSalarialConfirmadaPeriodo(
        @Param("usuarioId") Long usuarioId,
        @Param("inicio") LocalDateTime inicio,
        @Param("fim") LocalDateTime fim
    );

    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t WHERE t.usuario.id = :usuarioId "
        + "AND t.tipoTransacao = com.consumoesperto.model.Transacao$TipoTransacao.RECEITA "
        + "AND t.statusConferencia = com.consumoesperto.model.Transacao$StatusConferencia.PREVISTO "
        + "AND t.origemFiscal IN ("
        + "com.consumoesperto.model.OrigemProvisionamentoFiscal.DECIMO_TERCEIRO_UNICO, "
        + "com.consumoesperto.model.OrigemProvisionamentoFiscal.DECIMO_TERCEIRA_PRIMEIRA, "
        + "com.consumoesperto.model.OrigemProvisionamentoFiscal.DECIMO_TERCEIRA_SEGUNDA) "
        + "AND t.excluido = false "
        + "AND t.dataTransacao BETWEEN :inicio AND :fim")
    BigDecimal sumReceitaDecimoTerceiroPrevistaPeriodo(
        @Param("usuarioId") Long usuarioId,
        @Param("inicio") LocalDateTime inicio,
        @Param("fim") LocalDateTime fim
    );

    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t WHERE t.usuario.id = :usuarioId "
        + "AND t.tipoTransacao = com.consumoesperto.model.Transacao$TipoTransacao.RECEITA "
        + "AND t.statusConferencia = com.consumoesperto.model.Transacao$StatusConferencia.CONFIRMADA "
        + "AND t.origemFiscal IN ("
        + "com.consumoesperto.model.OrigemProvisionamentoFiscal.DECIMO_TERCEIRO_UNICO, "
        + "com.consumoesperto.model.OrigemProvisionamentoFiscal.DECIMO_TERCEIRA_PRIMEIRA, "
        + "com.consumoesperto.model.OrigemProvisionamentoFiscal.DECIMO_TERCEIRA_SEGUNDA) "
        + "AND t.excluido = false "
        + "AND t.dataTransacao BETWEEN :inicio AND :fim")
    BigDecimal sumReceitaDecimoTerceiroConfirmadaPeriodo(
        @Param("usuarioId") Long usuarioId,
        @Param("inicio") LocalDateTime inicio,
        @Param("fim") LocalDateTime fim
    );

    /** Receitas fiscais confirmadas já creditadas em conta (já compõem patrimônio multicarteira). */
    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t WHERE t.usuario.id = :usuarioId "
        + "AND t.tipoTransacao = com.consumoesperto.model.Transacao$TipoTransacao.RECEITA "
        + "AND t.statusConferencia = com.consumoesperto.model.Transacao$StatusConferencia.CONFIRMADA "
        + "AND t.origemFiscal IS NOT NULL AND t.contaBancaria IS NOT NULL AND t.fatura IS NULL "
        + "AND t.excluido = false "
        + "AND t.dataTransacao BETWEEN :inicio AND :fim")
    BigDecimal sumReceitaFiscalConfirmadaRefletidaPatrimonioPeriodo(
        @Param("usuarioId") Long usuarioId,
        @Param("inicio") LocalDateTime inicio,
        @Param("fim") LocalDateTime fim
    );

    /** Transações confirmadas vinculadas à conta — base da reconciliação idempotente de saldo. */
    @Query("SELECT t FROM Transacao t WHERE t.contaBancaria.id = :contaId "
        + "AND t.excluido = false "
        + "AND t.statusConferencia = com.consumoesperto.model.Transacao$StatusConferencia.CONFIRMADA "
        + "ORDER BY t.dataTransacao ASC, t.id ASC")
    List<Transacao> findEfetivadasPorConta(@Param("contaId") Long contaId);

    @Query("SELECT t FROM Transacao t WHERE t.usuario.id = :usuarioId "
        + "AND t.excluido = false "
        + "AND t.statusConferencia = com.consumoesperto.model.Transacao$StatusConferencia.PREVISTO "
        + "AND t.origemFiscal IS NOT NULL "
        + "ORDER BY t.dataTransacao DESC")
    List<Transacao> findProvisoesFiscaisPrevistasByUsuario(@Param("usuarioId") Long usuarioId);

    /** Parcelas de empréstimo PREVISTO ativas (compromisso mensal recorrente por empréstimo). */
    @Query("SELECT t FROM Transacao t WHERE t.usuario.id = :usuarioId "
        + "AND t.excluido = false "
        + "AND t.statusConferencia = com.consumoesperto.model.Transacao$StatusConferencia.PREVISTO "
        + "AND t.emprestimoId IS NOT NULL "
        + "AND t.tipoTransacao = com.consumoesperto.model.Transacao$TipoTransacao.DESPESA "
        + "ORDER BY t.emprestimoId ASC, t.parcelaAtual ASC")
    List<Transacao> findParcelasEmprestimoPrevistasAtivas(@Param("usuarioId") Long usuarioId);

    /** Parcelas de empréstimo PREVISTO que vencem no mês — projeção de caixa do mês. */
    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t WHERE t.usuario.id = :usuarioId "
        + "AND t.excluido = false "
        + "AND t.statusConferencia = com.consumoesperto.model.Transacao$StatusConferencia.PREVISTO "
        + "AND t.emprestimoId IS NOT NULL "
        + "AND t.tipoTransacao = com.consumoesperto.model.Transacao$TipoTransacao.DESPESA "
        + "AND t.dataTransacao BETWEEN :inicio AND :fim")
    BigDecimal sumParcelasEmprestimoPrevistasNoMes(
        @Param("usuarioId") Long usuarioId,
        @Param("inicio") LocalDateTime inicio,
        @Param("fim") LocalDateTime fim
    );

    @Query("SELECT t FROM Transacao t WHERE t.usuario.id = :usuarioId "
        + "AND t.excluido = false "
        + "AND t.emprestimoId = :emprestimoId "
        + "ORDER BY t.dataTransacao ASC, t.id ASC")
    List<Transacao> findByUsuarioIdAndEmprestimoIdOrderByDataTransacaoAsc(
        @Param("usuarioId") Long usuarioId,
        @Param("emprestimoId") String emprestimoId
    );

    @Query("SELECT DISTINCT t.emprestimoId FROM Transacao t WHERE t.usuario.id = :usuarioId "
        + "AND t.excluido = false "
        + "AND t.emprestimoId IS NOT NULL "
        + "ORDER BY t.emprestimoId DESC")
    List<String> findEmprestimoIdsByUsuario(@Param("usuarioId") Long usuarioId);

    /** Provisões PREVISTO vencidas (fantasmas) — fiscal ou despesa estimada. */
    @Query("SELECT t FROM Transacao t WHERE t.usuario.id = :usuarioId "
        + "AND t.statusConferencia = com.consumoesperto.model.Transacao$StatusConferencia.PREVISTO "
        + "AND t.excluido = false "
        + "AND t.dataTransacao < :limite "
        + "AND (t.origemFiscal IS NOT NULL "
        + "     OR t.tipoTransacao = com.consumoesperto.model.Transacao$TipoTransacao.DESPESA)")
    List<Transacao> findProvisoesFantasmas(
        @Param("usuarioId") Long usuarioId,
        @Param("limite") LocalDateTime limite
    );

    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t WHERE t.usuario.id = :usuarioId "
        + "AND t.statusConferencia = com.consumoesperto.model.Transacao$StatusConferencia.PREVISTO "
        + "AND t.excluido = false "
        + "AND t.dataTransacao < :limite "
        + "AND t.dataTransacao BETWEEN :inicio AND :fim "
        + "AND (t.origemFiscal IS NOT NULL "
        + "     OR t.tipoTransacao = com.consumoesperto.model.Transacao$TipoTransacao.DESPESA)")
    BigDecimal sumProvisoesFantasmasPeriodo(
        @Param("usuarioId") Long usuarioId,
        @Param("inicio") LocalDateTime inicio,
        @Param("fim") LocalDateTime fim,
        @Param("limite") LocalDateTime limite
    );
}
