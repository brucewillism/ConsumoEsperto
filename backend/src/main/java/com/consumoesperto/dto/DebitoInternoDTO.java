package com.consumoesperto.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebitoInternoDTO {
    private Long id;
    private Long credorId;
    private String credorNome;
    private Long devedorId;
    private String devedorNome;
    private BigDecimal valor;
    private String descricao;
    private boolean liquidado;
    private LocalDateTime dataCriacao;
    private LocalDateTime dataLiquidacao;
}
