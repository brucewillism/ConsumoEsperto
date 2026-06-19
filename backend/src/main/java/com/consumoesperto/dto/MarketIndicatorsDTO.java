package com.consumoesperto.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MarketIndicatorsDTO {
    /** Selic referência (% a.a.), quando disponível. */
    private BigDecimal selicAa;
    /** Taxa média consignado público PF (% a.a.) — BCB série 25497 ou Selic+spread. */
    private BigDecimal taxaMediaConsignadoAa;
    /** IPCA ou inflação mensal de referência (%), quando disponível. */
    private BigDecimal ipcaMes;
    /** USD/BRL (venda ou compra conforme fonte). */
    private BigDecimal dolarBrl;
    private String fonteResumo;
    private Boolean dadosParciais;
}
