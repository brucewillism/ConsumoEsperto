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
public class TransferBetweenAccountsStructuredDTO {

    @NotBlank
    private String action;

    @NotNull
    @DecimalMin("0.01")
    @JsonAlias({"valor", "valorTotal"})
    private BigDecimal amount;

    @NotBlank
    @JsonAlias({"accountOrigin", "conta_origem", "originAccount"})
    private String contaOrigem;

    @NotBlank
    @JsonAlias({"accountDestination", "conta_destino", "destinationAccount"})
    private String contaDestino;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double confianca;
}
