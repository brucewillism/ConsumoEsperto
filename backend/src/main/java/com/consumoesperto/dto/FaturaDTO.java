package com.consumoesperto.dto;

import com.consumoesperto.model.Fatura;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FaturaDTO {

    private Long id;

    @NotNull(message = "Valor da fatura é obrigatório")
    private BigDecimal valorFatura;

    @NotNull(message = "Valor pago é obrigatório")
    private BigDecimal valorPago = BigDecimal.ZERO;

    private LocalDateTime dataVencimento;
    private LocalDateTime dataFechamento;
    private LocalDateTime dataPagamento;

    @NotNull(message = "Status da fatura é obrigatório")
    private Fatura.StatusFatura statusFatura = Fatura.StatusFatura.ABERTA;

    private String numeroFatura;
    private Long cartaoCreditoId;

    private LocalDateTime dataCriacao;
    private LocalDateTime dataAtualizacao;
}
