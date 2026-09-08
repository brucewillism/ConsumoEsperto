package com.consumoesperto.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Superfície mínima de {@code finance.transactions.search}.
 * Não é a entidade {@code Transacao}. Campos escolhidos:
 * <ul>
 *   <li>{@code id} — referência para o dono consultar no app</li>
 *   <li>{@code occurred_at} — quando ocorreu (filtro de período)</li>
 *   <li>{@code amount} / {@code type} — valor e direção</li>
 *   <li>{@code category_id}/{@code category} — recorte, não dump</li>
 *   <li>{@code account_id}/{@code card_id} — origem, sem saldo nem número de cartão</li>
 *   <li>{@code description} — texto do usuário, limitado; conteúdo não confiável</li>
 * </ul>
 * Fora de propósito: notas internas, fingerprint de ingestão, CNPJ, usuário, timestamps de auditoria.
 */
@Value
@Builder
public class FinanceTransactionSearchItemDto {
    Long id;
    LocalDateTime occurredAt;
    BigDecimal amount;
    String type;
    Long categoryId;
    String category;
    Long accountId;
    Long cardId;
    String description;
}
