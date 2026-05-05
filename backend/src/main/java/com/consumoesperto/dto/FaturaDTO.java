package com.consumoesperto.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO genérico para faturas de cartões de crédito
 * 
 * Este DTO é usado pelos serviços e controllers existentes
 * para manter compatibilidade com o código atual.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FaturaDTO {

    private Long id;

    @NotNull(message = "ID do cartão é obrigatório")
    private Long cartaoCreditoId;

    private String nomeCartao;
    private String banco;

    private BigDecimal valorTotal;

    private BigDecimal valorMinimo;

    // Campo adicional para compatibilidade com código existente
    private BigDecimal valorFatura;

    @NotNull(message = "Data de vencimento é obrigatória")
    private LocalDateTime dataVencimento;

    @NotNull(message = "Data de fechamento é obrigatória")
    private LocalDateTime dataFechamento;

    private String status;

    // Campo adicional para compatibilidade com código existente
    private com.consumoesperto.model.Fatura.StatusFatura statusFatura;

    private Boolean paga = false;

    private LocalDateTime dataPagamento;
    private BigDecimal valorPago;

    // Campo adicional para compatibilidade com código existente
    private String numeroFatura;

    private Long usuarioId;

    private LocalDateTime dataCriacao;
    private LocalDateTime dataAtualizacao;
}
