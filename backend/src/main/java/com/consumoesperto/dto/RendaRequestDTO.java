package com.consumoesperto.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class RendaRequestDTO {

    @NotBlank(message = "Informe a descrição da renda.")
    @Size(max = 200)
    private String descricao;

    @NotNull(message = "Informe o valor da renda.")
    @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero.")
    private BigDecimal valor;

    @NotNull(message = "Informe o dia de pagamento (1-31).")
    @Min(1)
    @Max(31)
    private Integer diaPagamento;

    @NotNull(message = "Informe a conta bancária de destino.")
    private Long contaDestinoId;

    /** Se true (padrão), credita o valor na conta ao salvar. */
    private Boolean creditarAgora = true;
}
