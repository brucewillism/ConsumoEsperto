package com.consumoesperto.dto.ai.structured;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import javax.validation.Valid;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContrachequeStructuredDTO {

    @NotBlank
    private String tipoDocumento;

    private String empresa;
    private Integer mes;
    private Integer ano;

    @NotNull
    @DecimalMin("0.01")
    @JsonAlias({"bruto", "salarioBrutoTotal"})
    private BigDecimal salarioBruto;

    @NotNull
    @DecimalMin("0.0")
    @JsonAlias({"liquido", "salarioLiquidoTotal"})
    private BigDecimal salarioLiquido;

    @Valid
    private List<DescontoContrachequeStructuredDTO> descontos;
}
