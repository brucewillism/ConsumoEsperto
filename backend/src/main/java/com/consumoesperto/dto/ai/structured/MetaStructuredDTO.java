package com.consumoesperto.dto.ai.structured;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetaStructuredDTO {

    @NotBlank
    private String action;

    @NotBlank
    @JsonAlias({"nome", "descricaoMeta", "nomeMeta"})
    private String description;

    @JsonAlias({"valor", "valorTotal", "valorObjetivo"})
    private BigDecimal amount;

    @JsonAlias({"percentual", "percentualComprometimento"})
    private BigDecimal percentualComprometimento;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double confianca;
}
