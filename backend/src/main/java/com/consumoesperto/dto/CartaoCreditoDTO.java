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
import javax.validation.constraints.Min;
import javax.validation.constraints.Max;
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
    @Size(min = 2, max = 100, message = "Nome do cartão deve ter entre 2 e 100 caracteres")
    @Pattern(regexp = "^[a-zA-Z0-9\\s\\-]+$", message = "Nome do cartão contém caracteres inválidos")
    private String nome;

    @NotBlank(message = "Banco é obrigatório")
    @Size(min = 2, max = 50, message = "Banco deve ter entre 2 e 50 caracteres")
    @Pattern(regexp = "^[a-zA-Z0-9\\s\\-]+$", message = "Nome do banco contém caracteres inválidos")
    private String banco;

    @NotBlank(message = "Número do cartão é obrigatório")
    @Size(min = 13, max = 19, message = "Número do cartão deve ter entre 13 e 19 dígitos")
    @Pattern(regexp = "^[0-9]+$", message = "Número do cartão deve conter apenas dígitos")
    private String numeroCartao;

    @NotNull(message = "Limite de crédito é obrigatório")
    @DecimalMin(value = "0.01", message = "Limite de crédito deve ser maior que zero")
    @DecimalMax(value = "999999.99", message = "Limite de crédito não pode exceder R$ 999.999,99")
    private BigDecimal limiteCredito;

    @NotNull(message = "Limite disponível é obrigatório")
    @DecimalMin(value = "0.00", message = "Limite disponível não pode ser negativo")
    @DecimalMax(value = "999999.99", message = "Limite disponível não pode exceder R$ 999.999,99")
    private BigDecimal limiteDisponivel;

<<<<<<< HEAD
    @Min(value = 1, message = "Dia de vencimento deve ser entre 1 e 31")
    @Max(value = 31, message = "Dia de vencimento deve ser entre 1 e 31")
=======
>>>>>>> origin/main
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
