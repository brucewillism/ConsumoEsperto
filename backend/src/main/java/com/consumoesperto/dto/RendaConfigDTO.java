package com.consumoesperto.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RendaConfigDTO {
    private BigDecimal salarioBruto;
    private List<DescontoFixoDTO> descontosFixos;
    private Integer diaPagamento;
    private BigDecimal salarioLiquido;
    private BigDecimal totalDescontos;
    /** Percentual dos descontos sobre o bruto (0–100+), null se bruto zero. */
    private BigDecimal percentualDescontosSobreBruto;
    private boolean receitaAutomaticaAtiva;

    public static RendaConfigDTO vazio() {
        return RendaConfigDTO.builder()
            .salarioBruto(BigDecimal.ZERO)
            .descontosFixos(new ArrayList<>())
            .diaPagamento(null)
            .salarioLiquido(BigDecimal.ZERO)
            .totalDescontos(BigDecimal.ZERO)
            .percentualDescontosSobreBruto(BigDecimal.ZERO)
            .receitaAutomaticaAtiva(false)
            .build();
    }
}
