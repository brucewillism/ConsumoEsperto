package com.consumoesperto.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContaBancariaDTO {

    private Long id;

    @NotBlank(message = "Nome da conta é obrigatório")
    @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres")
    private String nome;

    @NotNull(message = "Tipo da conta é obrigatório")
    private TipoConta tipo;

    /** Saldo inicial no cadastro; após criação reflete movimentações. */
    @NotNull(message = "Saldo é obrigatório")
    @DecimalMin(value = "0.00", message = "Saldo não pode ser negativo")
    private BigDecimal saldoAtual;

    private Long usuarioId;
    private boolean ativa = true;
    private boolean padrao = false;
    private LocalDateTime dataCriacao;
    private LocalDateTime dataAtualizacao;

    public enum TipoConta {
        CORRENTE, POUPANCA, DINHEIRO
    }
}
