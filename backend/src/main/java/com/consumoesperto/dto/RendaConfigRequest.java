package com.consumoesperto.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class RendaConfigRequest {
    private BigDecimal salarioBruto;
    private List<DescontoFixoDTO> descontosFixos;
    private Integer diaPagamento;
    private Boolean receitaAutomaticaAtiva;
}
