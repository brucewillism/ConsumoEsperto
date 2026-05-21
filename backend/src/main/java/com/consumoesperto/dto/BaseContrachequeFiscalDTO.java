package com.consumoesperto.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Base salarial extraída do último contracheque confirmado ou da configuração de renda. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaseContrachequeFiscalDTO {
    private BigDecimal salarioBruto;
    private BigDecimal salarioLiquido;
    /** Bruto − líquido (INSS, IRRF e demais descontos tributários do holerite). */
    private BigDecimal descontosImposto;
    private Integer mesReferencia;
    private Integer anoReferencia;
    private String fonte;
    private boolean estimado;
}
