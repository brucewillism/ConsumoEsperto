package com.consumoesperto.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgendamentoPagamentoDTO {
    private Long id;
    private Long contaDebitoId;
    private String contaDebitoNome;
    private String beneficiario;
    private BigDecimal valor;
    private LocalDate dataVencimento;
    private String codigoBarrasOuPix;
    private String status;
    private LocalDateTime dataCriacao;
    private LocalDateTime dataProcessamento;
    private String mensagemErro;
    private String recorrencia;
    private LocalDate dataFim;
    private LocalDate proximaExecucao;
    private LocalDate ultimaExecucao;
    private Integer diaVencimentoMensal;
    private Long categoriaId;
    private String categoriaNome;
    private Long cartaoCreditoId;
    private String cartaoCreditoNome;
    private Integer falhasConsecutivas;
}
