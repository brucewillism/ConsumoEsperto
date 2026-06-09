package com.consumoesperto.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssinaturaRecorrenteDTO {
    private Long id;
    private String nome;
    private BigDecimal valor;
    private Integer diaVencimento;
    private Long contaDebitoPadraoId;
    private String contaDebitoPadraoNome;
    private boolean ativo;
}
