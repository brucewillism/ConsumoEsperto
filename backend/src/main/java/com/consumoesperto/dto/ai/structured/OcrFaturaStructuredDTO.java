package com.consumoesperto.dto.ai.structured;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OcrFaturaStructuredDTO {

    @NotBlank
    private String tipoDocumento;

    @JsonAlias({"cartao", "banco"})
    private String bancoCartao;

    private LocalDate dataVencimento;
    private LocalDate dataFechamento;

    @JsonAlias({"total", "valorFatura"})
    private BigDecimal valorTotal;

    @NotNull
    @NotEmpty
    @Valid
    private List<LancamentoFaturaStructuredDTO> lancamentos;
}
