package com.consumoesperto.dto.ai.structured;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LancamentoFaturaStructuredDTO {

    private LocalDate data;

    @NotBlank
    private String descricao;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal valor;

    private Integer parcelaAtual;
    private Integer totalParcelas;
}
