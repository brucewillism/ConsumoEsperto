package com.consumoesperto.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO para faturas de cartões de crédito do Mercado Pago
 * 
 * Este DTO contém os dados das faturas obtidos
 * da API do Mercado Pago.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MercadoPagoFaturaDTO {

    /**
     * ID único da fatura no Mercado Pago
     */
    private String id;

    /**
     * ID do cartão de crédito
     */
    private String cartaoId;

    /**
     * Nome do cartão
     */
    private String nomeCartao;

    /**
     * Valor total da fatura
     */
    private BigDecimal valorTotal;

    /**
     * Valor mínimo para pagamento
     */
    private BigDecimal valorMinimo;

    /**
     * Data de vencimento da fatura
     */
    private LocalDate dataVencimento;

    /**
     * Data de fechamento da fatura
     */
    private LocalDate dataFechamento;

    /**
     * Status da fatura (ex: "Aberta", "Fechada", "Vencida")
     */
    private String status;

    /**
     * Indica se a fatura foi paga
     */
    private Boolean paga = false;

    /**
     * Data de pagamento (se paga)
     */
    private LocalDate dataPagamento;

    /**
     * Valor pago (se paga)
     */
    private BigDecimal valorPago;

    /**
     * Descrição da fatura
     */
    private String descricao;

    /**
     * Valor da fatura (alias para valorTotal)
     */
    private BigDecimal valor;

    /**
     * Tipo da fatura
     */
    private String tipo;
}
