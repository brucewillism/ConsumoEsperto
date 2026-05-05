package com.consumoesperto.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MetaFinanceiraListResponse {
    private List<MetaFinanceiraDTO> metas;
    /** Soma dos percentuais de comprometimento de todas as metas do usuário. */
    private BigDecimal totalPercentualComprometido;
    /** Aviso quando a soma ultrapassa o limiar configurado (ex.: 15%). */
    private String alertaComprometimento;
}
