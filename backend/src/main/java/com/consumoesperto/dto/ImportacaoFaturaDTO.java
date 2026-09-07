package com.consumoesperto.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class ImportacaoFaturaDTO {
    private Long id;
    private Long cartaoCreditoId;
    private String cartaoCreditoNome;
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
    /** Banco do Brasil: aguarda sim/não para somar saldo anterior ao total. */
    private Boolean aguardandoEscolhaSaldoAnterior;
    private BigDecimal saldoFaturaAnterior;
    private BigDecimal saldoFaturaAtual;
    /** Soma de todos os lançamentos listados na importação. */
    private BigDecimal somaLancamentos;
    /** |valorTotal − somaLancamentos| (conciliação). */
    private BigDecimal diferencaLancamentos;
    /** ABERTA ou PAGA_NO_BANCO — detectado automaticamente no PDF. */
    private String situacaoLeituraPdf;

    private String tipoArquivo;
    private String arquivoNome;
    private Long contaBancariaId;
    private String contaBancariaNome;
    private Boolean precisaEscolhaRecurso;
    private LocalDateTime periodoInicio;
    private LocalDateTime periodoFim;
    private Integer quantidadeLinhas;
    private Integer quantidadeValidas;
    private Integer quantidadeInvalidas;
    private BigDecimal totalDespesas;
    private BigDecimal totalReceitas;
    private Integer duplicadas;
    private Integer matchedExistentes;
    private Integer necessitamRevisao;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCartaoCreditoId() { return cartaoCreditoId; }
    public void setCartaoCreditoId(Long cartaoCreditoId) { this.cartaoCreditoId = cartaoCreditoId; }

    public String getCartaoCreditoNome() { return cartaoCreditoNome; }
    public void setCartaoCreditoNome(String cartaoCreditoNome) { this.cartaoCreditoNome = cartaoCreditoNome; }

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

    public Boolean getAguardandoEscolhaSaldoAnterior() { return aguardandoEscolhaSaldoAnterior; }
    public void setAguardandoEscolhaSaldoAnterior(Boolean aguardandoEscolhaSaldoAnterior) {
        this.aguardandoEscolhaSaldoAnterior = aguardandoEscolhaSaldoAnterior;
    }

    public BigDecimal getSaldoFaturaAnterior() { return saldoFaturaAnterior; }
    public void setSaldoFaturaAnterior(BigDecimal saldoFaturaAnterior) { this.saldoFaturaAnterior = saldoFaturaAnterior; }

    public BigDecimal getSaldoFaturaAtual() { return saldoFaturaAtual; }
    public void setSaldoFaturaAtual(BigDecimal saldoFaturaAtual) { this.saldoFaturaAtual = saldoFaturaAtual; }

    public BigDecimal getSomaLancamentos() { return somaLancamentos; }
    public void setSomaLancamentos(BigDecimal somaLancamentos) { this.somaLancamentos = somaLancamentos; }

    public BigDecimal getDiferencaLancamentos() { return diferencaLancamentos; }
    public void setDiferencaLancamentos(BigDecimal diferencaLancamentos) { this.diferencaLancamentos = diferencaLancamentos; }

    public String getSituacaoLeituraPdf() { return situacaoLeituraPdf; }
    public void setSituacaoLeituraPdf(String situacaoLeituraPdf) { this.situacaoLeituraPdf = situacaoLeituraPdf; }

    public String getTipoArquivo() { return tipoArquivo; }
    public void setTipoArquivo(String tipoArquivo) { this.tipoArquivo = tipoArquivo; }

    public String getArquivoNome() { return arquivoNome; }
    public void setArquivoNome(String arquivoNome) { this.arquivoNome = arquivoNome; }

    public Long getContaBancariaId() { return contaBancariaId; }
    public void setContaBancariaId(Long contaBancariaId) { this.contaBancariaId = contaBancariaId; }

    public String getContaBancariaNome() { return contaBancariaNome; }
    public void setContaBancariaNome(String contaBancariaNome) { this.contaBancariaNome = contaBancariaNome; }

    public Boolean getPrecisaEscolhaRecurso() { return precisaEscolhaRecurso; }
    public void setPrecisaEscolhaRecurso(Boolean precisaEscolhaRecurso) { this.precisaEscolhaRecurso = precisaEscolhaRecurso; }

    public LocalDateTime getPeriodoInicio() { return periodoInicio; }
    public void setPeriodoInicio(LocalDateTime periodoInicio) { this.periodoInicio = periodoInicio; }

    public LocalDateTime getPeriodoFim() { return periodoFim; }
    public void setPeriodoFim(LocalDateTime periodoFim) { this.periodoFim = periodoFim; }

    public Integer getQuantidadeLinhas() { return quantidadeLinhas; }
    public void setQuantidadeLinhas(Integer quantidadeLinhas) { this.quantidadeLinhas = quantidadeLinhas; }

    public Integer getQuantidadeValidas() { return quantidadeValidas; }
    public void setQuantidadeValidas(Integer quantidadeValidas) { this.quantidadeValidas = quantidadeValidas; }

    public Integer getQuantidadeInvalidas() { return quantidadeInvalidas; }
    public void setQuantidadeInvalidas(Integer quantidadeInvalidas) { this.quantidadeInvalidas = quantidadeInvalidas; }

    public BigDecimal getTotalDespesas() { return totalDespesas; }
    public void setTotalDespesas(BigDecimal totalDespesas) { this.totalDespesas = totalDespesas; }

    public BigDecimal getTotalReceitas() { return totalReceitas; }
    public void setTotalReceitas(BigDecimal totalReceitas) { this.totalReceitas = totalReceitas; }

    public Integer getDuplicadas() { return duplicadas; }
    public void setDuplicadas(Integer duplicadas) { this.duplicadas = duplicadas; }

    public Integer getMatchedExistentes() { return matchedExistentes; }
    public void setMatchedExistentes(Integer matchedExistentes) { this.matchedExistentes = matchedExistentes; }

    public Integer getNecessitamRevisao() { return necessitamRevisao; }
    public void setNecessitamRevisao(Integer necessitamRevisao) { this.necessitamRevisao = necessitamRevisao; }
}
