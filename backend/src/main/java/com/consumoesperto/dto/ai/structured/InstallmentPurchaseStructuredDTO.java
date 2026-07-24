package com.consumoesperto.dto.ai.structured;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
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
public class InstallmentPurchaseStructuredDTO {

    @NotBlank
    private String action;

    @NotNull
    @DecimalMin("0.01")
    @JsonAlias({"valor", "valorTotal", "purchasePrice"})
    private BigDecimal amount;

    @NotBlank
    @JsonAlias({"descricao", "descricaoItem"})
    private String description;

    @NotNull
    @Min(2)
    @JsonAlias({"installments", "parcelas"})
    private Integer installmentCount;

    @JsonAlias({"cardName", "apelido"})
    private String cardName;

    @JsonAlias({"bancoCartao", "instituicao"})
    private String bank;

    @JsonAlias({"valorParcela", "installmentAmount"})
    private BigDecimal installmentAmount;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double confianca;
}
