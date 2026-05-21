package com.consumoesperto.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Resultado do cálculo CLT do 13º salário. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultadoDecimoTerceiroDTO {
    /** Líquido total estimado do 13º (= líquido mensal do contracheque). */
    private BigDecimal liquidoTotalEstimado;
    /** 50% do bruto — sem descontos (1ª parcela). */
    private BigDecimal primeiraParcelaBruta;
    /** Saldo líquido após retenções (2ª parcela, dezembro). */
    private BigDecimal segundaParcelaLiquida;
    /** Valor integral em parcela única. */
    private BigDecimal parcelaUnicaLiquida;
}
