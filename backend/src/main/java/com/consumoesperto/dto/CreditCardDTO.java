package com.consumoesperto.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO para cartões de crédito compatível com o frontend
 * 
 * Este DTO tem os campos que o frontend espera:
 * - name (nome)
 * - number (numeroCartao)
 * - limit (limiteCredito)
 * - available (limiteDisponivel)
 * - bank (banco)
 * - type (tipoCartao)
 * - status (ativo)
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreditCardDTO {

    private Long id;
    private String name;
    private String number;
    private BigDecimal limit;
    private BigDecimal available;
    private String bank;
    private String type;
    private String status;
    private Long usuarioId;
}
