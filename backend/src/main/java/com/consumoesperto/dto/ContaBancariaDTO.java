package com.consumoesperto.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
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

    /** Saldo inicial no cadastro; após criação reflete movimentações (pode ser negativo — cheque especial). */
    @NotNull(message = "Saldo é obrigatório")
    private BigDecimal saldoAtual;

    /** Limite de cheque especial (>= 0). Não soma ao saldo; permite o saldo ficar negativo até este valor. */
    @NotNull
    @DecimalMin(value = "0.00", message = "O limite de cheque especial não pode ser negativo.")
    @Digits(integer = 13, fraction = 2)
    private BigDecimal limiteChequeEspecial = BigDecimal.ZERO;

    private Long usuarioId;
    private boolean ativa = true;
    private boolean padrao = false;
    private LocalDateTime dataCriacao;
    private LocalDateTime dataAtualizacao;

    public enum TipoConta {
        CORRENTE, POUPANCA, DINHEIRO
    }
}
