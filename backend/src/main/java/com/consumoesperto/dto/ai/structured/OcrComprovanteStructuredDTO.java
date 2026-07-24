package com.consumoesperto.dto.ai.structured;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
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
public class OcrComprovanteStructuredDTO {

    @NotNull
    @DecimalMin("0.01")
    @JsonAlias({"amount", "valorTotal"})
    private BigDecimal valor;

    @NotBlank
    @Pattern(regexp = "RECEITA|DESPESA", message = "tipo deve ser RECEITA ou DESPESA")
    private String tipo;

    private LocalDate data;

    @NotBlank
    @JsonAlias({"description", "beneficiario", "pagador"})
    private String descricao;

    @JsonAlias({"categoryName", "categoriaNome"})
    private String categoria;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double confianca;
}
