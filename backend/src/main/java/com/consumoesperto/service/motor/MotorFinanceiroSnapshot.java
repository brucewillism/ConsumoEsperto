package com.consumoesperto.service.motor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Snapshot auditável coletado do banco — entrada única para todos os motores determinísticos.
 */
public record MotorFinanceiroSnapshot(
    Long usuarioId,
    BigDecimal patrimonioLiquido,
    BigDecimal saldoContasDisponivel,
    BigDecimal rendaMensalMedia,
    BigDecimal saldoProjetadoFimMes,
    BigDecimal gastoProjetadoMes,
    BigDecimal receitasPrevistasMes,
    BigDecimal despesasPrevistasMes,
    BigDecimal faturasPendentesTotal,
    BigDecimal limiteCreditoTotal,
    BigDecimal utilizacaoCreditoPct,
    BigDecimal mesesReserva,
    List<BigDecimal> despesasMensaisUltimos6,
    List<BigDecimal> receitasMensaisUltimos6,
    int orcamentosTotal,
    int orcamentosNoVerde,
    int orcamentosEstourados,
    int transacoesParceladas6m,
    int comprasForaOrcamento6m,
    BigDecimal comprometimentoMetasPct,
    BigDecimal mediaProgressoMetasPct,
    List<MetaSnapshot> metas
) {
    public record MetaSnapshot(
        Long id,
        String descricao,
        BigDecimal valorAlvo,
        BigDecimal valorAcumulado,
        java.time.LocalDate dataAlvo,
        java.time.LocalDate dataCriacao,
        BigDecimal percentualComprometimento
    ) {}
}
