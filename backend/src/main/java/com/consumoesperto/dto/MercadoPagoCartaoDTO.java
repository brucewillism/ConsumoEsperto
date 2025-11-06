package com.consumoesperto.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO para cartões de crédito do Mercado Pago
 * 
 * Este DTO contém os dados dos cartões de crédito
 * obtidos da API do Mercado Pago.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MercadoPagoCartaoDTO {

    /**
     * ID único do cartão no Mercado Pago
     */
    private String id;

    /**
     * Nome do cartão (ex: "Nubank", "Itaú")
     */
    private String nome;

    /**
     * Últimos 4 dígitos do cartão
     */
    private String ultimosDigitos;

    /**
     * Limite total do cartão
     */
    private BigDecimal limiteTotal;

    /**
     * Limite disponível para uso
     */
    private BigDecimal limiteDisponivel;

    /**
     * Limite já utilizado
     */
    private BigDecimal limiteUtilizado;

    /**
     * Data de vencimento da próxima fatura
     */
    private LocalDate vencimentoFatura;

    /**
     * Valor da próxima fatura
     */
    private BigDecimal valorFatura;

    /**
     * Indica se o cartão está ativo
     */
    private Boolean ativo = true;

    /**
     * Bandeira do cartão (ex: "Visa", "Mastercard")
     */
    private String bandeira;

    /**
     * Tipo do cartão (ex: "Crédito", "Débito")
     */
    private String tipo;
}
