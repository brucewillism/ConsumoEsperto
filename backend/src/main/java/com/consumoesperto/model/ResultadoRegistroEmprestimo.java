package com.consumoesperto.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Resultado determinístico do registro de empréstimo consignado — números prontos para narração IA. */
@Data
@Builder(toBuilder = true)
public class ResultadoRegistroEmprestimo {

    private String emprestimoId;
    private BigDecimal valorTomado;
    private BigDecimal valorParcela;
    private int quantidadeParcelas;
    private boolean parcelaEstimada;

    private BigDecimal taxaJurosMensalPct;
    private BigDecimal taxaJurosAnualPct;
    private BigDecimal totalAPagar;
    private BigDecimal jurosTotais;

    private Long contaId;
    private String contaNome;
    private BigDecimal novoSaldoConta;

    private BigDecimal rendaLivreAntes;
    private BigDecimal rendaLivreDepois;
    private BigDecimal pctRendaComprometidaAntes;
    private BigDecimal pctRendaComprometidaDepois;

    private LocalDate dataPrimeiraParcela;
    private LocalDate dataUltimaParcela;

    private boolean precisaConfirmacao;
    private String motivoConfirmacao;
    private List<String> contasAmbiguas;
    private String mensagemConfirmacao;

    private boolean registrado;
    private int transacoesCriadas;
}
