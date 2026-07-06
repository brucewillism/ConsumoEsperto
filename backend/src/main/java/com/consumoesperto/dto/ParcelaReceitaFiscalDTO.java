package com.consumoesperto.dto;

import com.consumoesperto.model.OrigemProvisionamentoFiscal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParcelaReceitaFiscalDTO {
    private OrigemProvisionamentoFiscal origem;
    private String rotulo;
    private int mes;
    private int dia;
    /** Ano-calendário efetivo (obrigação vencida no ano corrente pode ir para ano+1). */
    private Integer ano;
    private BigDecimal valor;
    private String observacao;
}
