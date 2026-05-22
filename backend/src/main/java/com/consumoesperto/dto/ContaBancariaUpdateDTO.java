package com.consumoesperto.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * Atualização de conta — saldo não é editável via API (movimentado por transações).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContaBancariaUpdateDTO {

    @NotBlank(message = "Nome da conta é obrigatório")
    @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres")
    private String nome;

    @NotNull(message = "Tipo da conta é obrigatório")
    private ContaBancariaDTO.TipoConta tipo;

    private boolean ativa = true;
    private boolean padrao = false;
}
