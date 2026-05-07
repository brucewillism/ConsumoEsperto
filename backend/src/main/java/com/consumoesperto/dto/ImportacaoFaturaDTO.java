package com.consumoesperto.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class ImportacaoFaturaDTO {
    private Long id;
    private Long cartaoCreditoId;
    private String bancoCartao;
    private LocalDateTime dataVencimento;
    private LocalDateTime dataFechamento;
    private BigDecimal valorTotal;
    private BigDecimal pagamentoMinimo;
    private String status;
    private int novosDetectados;
    private List<ImportacaoFaturaItemDTO> itens;
    private List<String> auditorias;
    private LocalDateTime dataCriacao;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCartaoCreditoId() { return cartaoCreditoId; }
    public void setCartaoCreditoId(Long cartaoCreditoId) { this.cartaoCreditoId = cartaoCreditoId; }

    public String getBancoCartao() { return bancoCartao; }
    public void setBancoCartao(String bancoCartao) { this.bancoCartao = bancoCartao; }

    public LocalDateTime getDataVencimento() { return dataVencimento; }
    public void setDataVencimento(LocalDateTime dataVencimento) { this.dataVencimento = dataVencimento; }

    public LocalDateTime getDataFechamento() { return dataFechamento; }
    public void setDataFechamento(LocalDateTime dataFechamento) { this.dataFechamento = dataFechamento; }

    public BigDecimal getValorTotal() { return valorTotal; }
    public void setValorTotal(BigDecimal valorTotal) { this.valorTotal = valorTotal; }

    public BigDecimal getPagamentoMinimo() { return pagamentoMinimo; }
    public void setPagamentoMinimo(BigDecimal pagamentoMinimo) { this.pagamentoMinimo = pagamentoMinimo; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getNovosDetectados() { return novosDetectados; }
    public void setNovosDetectados(int novosDetectados) { this.novosDetectados = novosDetectados; }

    public List<ImportacaoFaturaItemDTO> getItens() { return itens; }
    public void setItens(List<ImportacaoFaturaItemDTO> itens) { this.itens = itens; }

    public List<String> getAuditorias() { return auditorias; }
    public void setAuditorias(List<String> auditorias) { this.auditorias = auditorias; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }
}
