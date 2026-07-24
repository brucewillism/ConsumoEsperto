package com.consumoesperto.dto.ai.structured;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
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
public class BudgetStructuredDTO {

    @NotBlank
    private String action;

    @NotBlank
    @JsonAlias({"categoria", "identifier", "searchPhrase"})
    private String categoryName;

    @NotNull
    @DecimalMin("0.01")
    @JsonAlias({"valor", "valorTotal", "amount", "limite"})
    private BigDecimal budgetLimit;

    @Min(1)
    @Max(12)
    private Integer reportMonth;

    @Min(2000)
    private Integer reportYear;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double confianca;
}
