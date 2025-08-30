package com.consumoesperto.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO genérico para cartões de crédito
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
public class CartaoCreditoDTO {

    private Long id;

    @NotBlank(message = "Nome do cartão é obrigatório")
    @Size(max = 100, message = "Nome do cartão deve ter no máximo 100 caracteres")
    private String nome;

    @NotBlank(message = "Banco é obrigatório")
    @Size(max = 50, message = "Banco deve ter no máximo 50 caracteres")
    private String banco;

    @NotBlank(message = "Número do cartão é obrigatório")
    @Size(max = 20, message = "Número do cartão deve ter no máximo 20 caracteres")
    private String numeroCartao;

    @NotNull(message = "Limite de crédito é obrigatório")
    private BigDecimal limiteCredito;

    @NotNull(message = "Limite disponível é obrigatório")
    private BigDecimal limiteDisponivel;

    private Integer diaVencimento;

    private Boolean ativo = true;

    private String cor;

    private String icone;

    // Campos de compatibilidade para código existente
    private LocalDateTime dataVencimento;
    
    private com.consumoesperto.model.CartaoCredito.TipoCartao tipoCartao;
    
    private LocalDateTime dataAtualizacao;

    private Long usuarioId;

    private LocalDateTime dataCriacao;
}
