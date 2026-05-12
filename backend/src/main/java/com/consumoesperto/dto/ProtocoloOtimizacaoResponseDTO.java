package com.consumoesperto.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProtocoloOtimizacaoResponseDTO {

    private String mensagemJarvis;
    /** Fator médio aplicado (ex.: 0,80 = corte médio de 20%). */
    private BigDecimal fatorAjusteEmergenciaMedio;
    private int percentualMedioReducaoTetos;
    private BigDecimal sobrevidaSaldoProjetado;
    private BigDecimal novoSaldoProjetadoFimMes;
    private List<AjusteOrcamentoDTO> ajustes = new ArrayList<>();
    /** Série completa pós-protocolo (efeito “novo futuro” no gráfico). */
    private PrevisaoFuturoChartDTO previsaoAjustada;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AjusteOrcamentoDTO {
        private Long orcamentoId;
        private String categoriaNome;
        private BigDecimal limiteAnterior;
        private BigDecimal limiteNovo;
    }
}
