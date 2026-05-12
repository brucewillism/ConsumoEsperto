package com.consumoesperto.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DisponibilidadeRealDTO {
    private BigDecimal saldoBancarioAtual;
    private BigDecimal totalContasFixasMes;
    private BigDecimal totalFaturasCartaoPendentes;
    private BigDecimal totalObrigacoes;
    private BigDecimal disponivelAposObrigacoes;
    /** Percentual do saldo atual que sobra para gastos variáveis (0–100+). */
    private BigDecimal percentualDisponivelVariavel;
    private int diasRestantesNoMes;
    private String mensagemJarvis;
}
