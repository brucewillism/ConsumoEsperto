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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CartaoStructuredDTO {

    @NotBlank
    private String action;

    @NotBlank
    @JsonAlias({"apelido", "nomeCartao", "newCardName"})
    private String cardName;

    @NotBlank
    @JsonAlias({"bancoCartao", "instituicao"})
    private String bank;

    @Min(1)
    @Max(31)
    @JsonAlias({"diaVencimento", "dueDay"})
    private Integer dueDay;

    @JsonAlias({"ultimosDigitos", "cardNumber"})
    private String cardNumber;

    @JsonAlias({"limite", "creditLimit", "newLimit"})
    private java.math.BigDecimal creditLimit;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double confianca;
}
