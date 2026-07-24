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
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OcrCupomStructuredDTO {

    @NotNull
    @DecimalMin("0.01")
    @JsonAlias({"valor", "total", "amount"})
    private BigDecimal valorTotal;

    @NotBlank
    @JsonAlias({"estabelecimentoNome", "loja", "merchant"})
    private String estabelecimento;

    @JsonAlias({"data", "dataCompra"})
    private LocalDate dataCompra;

    private String categoriaSugerida;
    private String cnpj;
    private String erro;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double confianca;
}
