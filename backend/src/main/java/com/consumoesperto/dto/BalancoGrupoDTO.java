package com.consumoesperto.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Balanço do racha-contas para um usuário dentro do seu grupo familiar.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalancoGrupoDTO {
    /** Débitos onde o usuário é credor (valores a receber). */
    private List<DebitoInternoDTO> aReceber;
    /** Débitos onde o usuário é devedor (suas pendências). */
    private List<DebitoInternoDTO> devidos;
    private BigDecimal totalAReceber;
    private BigDecimal totalDevido;
    /** Saldo líquido do usuário no grupo (aReceber - devido). */
    private BigDecimal saldoLiquido;
}
