package com.consumoesperto.dto;

import com.consumoesperto.model.CartaoCredito;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    private LocalDateTime dataVencimento;

    @NotNull(message = "Tipo do cartão é obrigatório")
    private CartaoCredito.TipoCartao tipoCartao;

    private Boolean ativo = true;

    private Long usuarioId;

    private LocalDateTime dataCriacao;
    private LocalDateTime dataAtualizacao;
}
