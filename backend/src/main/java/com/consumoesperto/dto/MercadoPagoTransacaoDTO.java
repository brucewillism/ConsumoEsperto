package com.consumoesperto.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO para transações do Mercado Pago
 * 
 * Este DTO representa uma transação retornada pela API do Mercado Pago,
 * incluindo informações sobre pagamentos, transferências e outras operações.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MercadoPagoTransacaoDTO {

    private String id;

    @NotBlank(message = "Descrição é obrigatória")
    private String descricao;

    @NotNull(message = "Valor é obrigatório")
    private BigDecimal valor;

    @NotNull(message = "Data da transação é obrigatória")
    private LocalDateTime dataTransacao;

    @NotBlank(message = "Tipo da transação é obrigatório")
    private String tipoTransacao;

    private String status;

    private String categoria;

    private String cartaoId;

    private String cartaoNome;

    private String banco;

    private String metodoPagamento;

    private String referencia;

    private String observacoes;

    private Boolean processada = false;

    private LocalDateTime dataProcessamento;

    private String usuarioId;

    private String contaId;

    private BigDecimal taxa;

    private String moeda = "BRL";

    private String localizacao;

    private String estabelecimento;

    private String codigoAutorizacao;

    private String numeroTransacao;

    private String numeroLote;

    private String numeroSequencial;

    private String codigoResposta;

    private String mensagemResposta;

    private String codigoErro;

    private String mensagemErro;

    private Boolean estornada = false;

    private LocalDateTime dataEstorno;

    private String motivoEstorno;

    private BigDecimal valorEstorno;

    private String numeroEstorno;

    private String referenciaEstorno;

    private String observacoesEstorno;

    private Boolean conciliada = false;

    private LocalDateTime dataConciliacao;

    private String numeroConciliacao;

    private String referenciaConciliacao;

    private String observacoesConciliacao;

    private Boolean exportada = false;

    private LocalDateTime dataExportacao;

    private String arquivoExportacao;

    private String observacoesExportacao;

    private Boolean ativa = true;

    private LocalDateTime dataCriacao;

    private LocalDateTime dataAtualizacao;
}
