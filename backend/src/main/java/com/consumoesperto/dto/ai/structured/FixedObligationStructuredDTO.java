package com.consumoesperto.dto.ai.structured;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
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
public class FixedObligationStructuredDTO {

    @NotBlank
    @Pattern(regexp = "CREATE_FIXED_EXPENSE|CREATE_SUBSCRIPTION")
    private String action;

    @NotBlank
    @JsonAlias({"nome", "descricaoItem", "identifier", "searchPhrase"})
    private String description;

    @DecimalMin("0.01")
    @JsonAlias({"valor", "valorTotal"})
    private BigDecimal amount;

    @Min(1)
    @Max(31)
    @JsonAlias({"diaVencimento", "dueDay"})
    private Integer dueDay;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double confianca;
}
