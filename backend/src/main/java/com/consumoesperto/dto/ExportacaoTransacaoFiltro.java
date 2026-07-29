package com.consumoesperto.dto;

import com.consumoesperto.model.Transacao;

import java.time.LocalDate;

/**
 * Filtros opcionais e combináveis para exportação CSV de transações.
 */
public class ExportacaoTransacaoFiltro {

    private LocalDate dataInicio;
    private LocalDate dataFim;
    private Long contaId;
    private Long cartaoId;
    private Long categoriaId;
    private Transacao.TipoTransacao tipoTransacao;
    private Transacao.StatusConferencia statusConferencia;
    private String descricaoContem;

    public LocalDate getDataInicio() { return dataInicio; }
    public void setDataInicio(LocalDate dataInicio) { this.dataInicio = dataInicio; }
    public LocalDate getDataFim() { return dataFim; }
    public void setDataFim(LocalDate dataFim) { this.dataFim = dataFim; }
    public Long getContaId() { return contaId; }
    public void setContaId(Long contaId) { this.contaId = contaId; }
    public Long getCartaoId() { return cartaoId; }
    public void setCartaoId(Long cartaoId) { this.cartaoId = cartaoId; }
    public Long getCategoriaId() { return categoriaId; }
    public void setCategoriaId(Long categoriaId) { this.categoriaId = categoriaId; }
    public Transacao.TipoTransacao getTipoTransacao() { return tipoTransacao; }
    public void setTipoTransacao(Transacao.TipoTransacao tipoTransacao) { this.tipoTransacao = tipoTransacao; }
    public Transacao.StatusConferencia getStatusConferencia() { return statusConferencia; }
    public void setStatusConferencia(Transacao.StatusConferencia statusConferencia) { this.statusConferencia = statusConferencia; }
    public String getDescricaoContem() { return descricaoContem; }
    public void setDescricaoContem(String descricaoContem) { this.descricaoContem = descricaoContem; }
}
