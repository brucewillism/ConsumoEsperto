package com.consumoesperto.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Future;
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

    @NotBlank(message = "Nome do cartão é obrigatório")
    @Size(min = 2, max = 100, message = "Nome do cartão deve ter entre 2 e 100 caracteres")
    @Pattern(regexp = "^[a-zA-Z0-9\\s\\-]+$", message = "Nome do cartão contém caracteres inválidos")
    private String nomeCartao;

    @NotNull(message = "Valor total é obrigatório")
    @DecimalMin(value = "0.01", message = "Valor total deve ser maior que zero")
    @DecimalMax(value = "999999.99", message = "Valor total não pode exceder R$ 999.999,99")
    private BigDecimal valorTotal;

    @NotNull(message = "Valor mínimo é obrigatório")
    @DecimalMin(value = "0.01", message = "Valor mínimo deve ser maior que zero")
    @DecimalMax(value = "999999.99", message = "Valor mínimo não pode exceder R$ 999.999,99")
    private BigDecimal valorMinimo;

    // Campo adicional para compatibilidade com código existente
    private BigDecimal valorFatura;

    @NotNull(message = "Data de vencimento é obrigatória")
    private LocalDateTime dataVencimento;

    @NotNull(message = "Data de fechamento é obrigatória")
    private LocalDateTime dataFechamento;

    @NotBlank(message = "Status é obrigatório")
    @Size(min = 2, max = 20, message = "Status deve ter entre 2 e 20 caracteres")
    @Pattern(regexp = "^(ABERTA|FECHADA|PAGA|VENCIDA|CANCELADA)$", message = "Status deve ser: ABERTA, FECHADA, PAGA, VENCIDA ou CANCELADA")
    private String status;

    // Campo adicional para compatibilidade com código existente
    private com.consumoesperto.model.Fatura.StatusFatura statusFatura;

    @NotNull(message = "Indicador de paga é obrigatório")
    private Boolean paga = false;

    private LocalDateTime dataPagamento;
    private BigDecimal valorPago;

    // Campo adicional para compatibilidade com código existente
    private String numeroFatura;

    private Long usuarioId;

    private LocalDateTime dataCriacao;
    private LocalDateTime dataAtualizacao;
}
