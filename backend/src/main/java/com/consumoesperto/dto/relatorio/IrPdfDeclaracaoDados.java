package com.consumoesperto.dto.relatorio;

import java.math.BigDecimal;
import java.util.List;

/**
 * Dados para o PDF de apoio ao IR: resumo por categoria/CNPJ (confirmadas e pendentes) + detalhe analítico.
 * O CSV continua apenas com despesas confirmadas (mesma regra fiscal).
 */
public record IrPdfDeclaracaoDados(
    List<IrPdfLinhaVm> linhas,
    BigDecimal totalDespesas,
    List<IrPdfLinhaVm> linhasPendentes,
    BigDecimal totalPendentes,
    List<IrPdfDetalheVm> detalhes,
    long qtdLancamentosConfirmados,
    long qtdLancamentosPendentes
) {
    public boolean semDados() {
        return linhas.isEmpty() && linhasPendentes.isEmpty();
    }
}
