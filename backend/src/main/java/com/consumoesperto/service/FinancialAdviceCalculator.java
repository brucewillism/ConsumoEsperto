package com.consumoesperto.service;

import com.consumoesperto.model.ContextoFinanceiro;
import com.consumoesperto.model.PropostaFinanceira;
import com.consumoesperto.model.ResultadoConselho;
import com.consumoesperto.model.TipoOperacaoFinanceira;
import com.consumoesperto.model.Veredito;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculadora determinística do J.A.R.V.I.S. Advisor — toda aritmética e veredito em Java.
 * Stateless e thread-safe.
 */
@Service
@RequiredArgsConstructor
public class FinancialAdviceCalculator {

    private final MarketDataService marketDataService;

    public ResultadoConselho calcular(PropostaFinanceira proposta, ContextoFinanceiro ctx) {
        ResultadoConselho r = new ResultadoConselho();
        r.setTipoOperacao(proposta.getTipoOperacao());
        r.setDescricaoItem(proposta.getDescricaoItem() != null ? proposta.getDescricaoItem() : "item");
        if (ctx.getMesesReservaAtual() != null) {
            r.setMesesReservaAtual(ctx.getMesesReservaAtual());
        }

        boolean parcelado = PropostaFinanceira.ehParcelado(proposta)
            || proposta.getTipoOperacao() == TipoOperacaoFinanceira.EMPRESTIMO
            || proposta.getTipoOperacao() == TipoOperacaoFinanceira.CONSIGNADO
            || proposta.getTipoOperacao() == TipoOperacaoFinanceira.FINANCIAMENTO
            || proposta.getTipoOperacao() == TipoOperacaoFinanceira.COMPRA_PARCELADA;

        if (parcelado && proposta.getQuantidadeParcelas() != null && proposta.getValorParcela() != null) {
            calcularOperacaoParcelada(proposta, ctx, r);
        } else {
            calcularCompraAvista(proposta, ctx, r);
        }

        enriquecerBenchmark(r);
        r.setVeredito(determinarVeredito(r));
        return r;
    }

    private void calcularCompraAvista(PropostaFinanceira p, ContextoFinanceiro ctx, ResultadoConselho r) {
        BigDecimal valor = nz(p.getValorTotal());
        if (valor.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal saldoLivre = nz(ctx.getPatrimonioLiquido());
        r.setCustoTotal(valor);
        r.setSaldoAposCompra(saldoLivre.subtract(valor).setScale(2, RoundingMode.HALF_UP));

        BigDecimal gasto = ctx.getGastoMensalMedio();
        if (gasto != null && gasto.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal reservaApos = nz(ctx.getReservaEmergencia()).subtract(valor);
            r.setReservaMesesApos(reservaApos.divide(gasto, 1, RoundingMode.HALF_UP));
        }
        r.setComprometeReserva(
            r.getSaldoAposCompra().compareTo(BigDecimal.ZERO) < 0
                || (r.getReservaMesesApos() != null
                    && r.getReservaMesesApos().compareTo(BigDecimal.valueOf(3)) < 0)
        );
    }

    private void calcularOperacaoParcelada(PropostaFinanceira p, ContextoFinanceiro ctx, ResultadoConselho r) {
        BigDecimal valorTomado = p.getValorTotal();
        BigDecimal parcela = nz(p.getValorParcela());
        int n = p.getQuantidadeParcelas() != null ? p.getQuantidadeParcelas() : 0;
        if (n <= 0 || parcela.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal totalPago = parcela.multiply(BigDecimal.valueOf(n)).setScale(2, RoundingMode.HALF_UP);
        r.setCustoTotal(totalPago);

        if (valorTomado != null && valorTomado.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal jurosTotais = totalPago.subtract(valorTomado).max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
            r.setJurosTotais(jurosTotais);

            double taxaMensal = resolverTaxaMensal(valorTomado.doubleValue(), parcela.doubleValue(), n);
            double taxaAnual = Math.pow(1 + taxaMensal, 12) - 1;
            r.setTaxaJurosMensal(BigDecimal.valueOf(taxaMensal * 100).setScale(2, RoundingMode.HALF_UP));
            r.setTaxaJurosAnual(BigDecimal.valueOf(taxaAnual * 100).setScale(2, RoundingMode.HALF_UP));
        } else {
            r.setAvisoSemValorTomado(true);
        }

        BigDecimal renda = ctx.getRendaLiquidaMensal();
        if (renda != null && renda.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal comprometimentoAtual = nz(ctx.getDespesasFixas()).add(nz(ctx.getAssinaturas()));
            BigDecimal comprometimentoNovo = comprometimentoAtual.add(parcela);
            r.setPercentualRendaComprometida(
                comprometimentoNovo.divide(renda, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(1, RoundingMode.HALF_UP)
            );
        } else {
            r.setAvisoSemRenda(true);
        }
    }

    /**
     * Resolve taxa mensal por bisseção: PV = PMT × (1 − (1+i)^−n) / i
     */
    double resolverTaxaMensal(double pv, double pmt, int n) {
        if (pv <= 0 || pmt <= 0 || n <= 0 || pmt * n <= pv) {
            return 0.0;
        }
        double baixo = 0.0000001;
        double alto = 1.0;
        for (int iter = 0; iter < 100; iter++) {
            double meio = (baixo + alto) / 2;
            double pvCalc = pmt * (1 - Math.pow(1 + meio, -n)) / meio;
            if (pvCalc > pv) {
                baixo = meio;
            } else {
                alto = meio;
            }
        }
        return (baixo + alto) / 2;
    }

    private void enriquecerBenchmark(ResultadoConselho r) {
        if (r.getTaxaJurosAnual() == null) {
            return;
        }
        BigDecimal mercado = marketDataService.getTaxaMediaConsignadoResiliente();
        if (mercado == null || mercado.compareTo(BigDecimal.ZERO) <= 0) {
            r.setComparacaoMercado("INDISPONIVEL");
            return;
        }
        r.setTaxaMercadoReferencia(mercado);
        r.setComparacaoMercado(
            r.getTaxaJurosAnual().compareTo(mercado) > 0 ? "ACIMA_DA_MEDIA" : "DENTRO_DA_MEDIA"
        );
    }

    private Veredito determinarVeredito(ResultadoConselho r) {
        if (r.isComprometeReserva()) {
            return Veredito.RISCO_ALTO;
        }
        if (r.getSaldoAposCompra() != null && r.getSaldoAposCompra().compareTo(BigDecimal.ZERO) < 0) {
            return Veredito.RISCO_ALTO;
        }
        if (r.getPercentualRendaComprometida() != null
            && r.getPercentualRendaComprometida().compareTo(BigDecimal.valueOf(30)) > 0) {
            return Veredito.RISCO_ALTO;
        }
        if (r.getPercentualRendaComprometida() != null
            && r.getPercentualRendaComprometida().compareTo(BigDecimal.valueOf(20)) > 0) {
            return Veredito.ATENCAO;
        }
        if (r.getTaxaJurosMensal() != null
            && r.getTaxaJurosMensal().compareTo(BigDecimal.valueOf(3)) > 0) {
            return Veredito.ATENCAO;
        }
        if (r.getReservaMesesApos() != null
            && r.getReservaMesesApos().compareTo(BigDecimal.valueOf(6)) < 0) {
            return Veredito.ATENCAO;
        }
        return Veredito.SEGURO;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
