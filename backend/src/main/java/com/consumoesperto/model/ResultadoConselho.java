package com.consumoesperto.model;

import lombok.Data;

import java.math.BigDecimal;

/** Resultado determinístico do conselho — todos os números calculados em Java. */
@Data
public class ResultadoConselho {

    private TipoOperacaoFinanceira tipoOperacao;
    private String descricaoItem;
    private Veredito veredito;

    private BigDecimal custoTotal;
    private BigDecimal jurosTotais;
    private BigDecimal taxaJurosMensal;
    private BigDecimal taxaJurosAnual;

    private BigDecimal percentualRendaComprometida;
    private BigDecimal saldoAposCompra;
    private BigDecimal reservaMesesApos;
    private BigDecimal mesesReservaAtual;

    private boolean comprometeReserva;

    /** ACIMA_DA_MEDIA | DENTRO_DA_MEDIA | INDISPONIVEL */
    private String comparacaoMercado;
    private BigDecimal taxaMercadoReferencia;

    /** Quando true, juros não puderam ser calculados (valor tomado ausente). */
    private boolean avisoSemValorTomado;

    /** Quando true, % da renda não foi calculado (renda não configurada). */
    private boolean avisoSemRenda;
}
