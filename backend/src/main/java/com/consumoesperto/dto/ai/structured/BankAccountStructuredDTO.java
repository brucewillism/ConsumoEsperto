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
public class BankAccountStructuredDTO {

    @NotBlank
    private String action;

    @NotBlank
    @JsonAlias({"identifier", "description", "nome"})
    private String accountName;

    @JsonAlias({"tipo", "tipoConta"})
    private String accountType;

    @DecimalMin("0.0")
    @JsonAlias({"saldoInicial", "amount", "valor"})
    private BigDecimal initialBalance;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double confianca;
}
