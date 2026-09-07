package com.consumoesperto.model;

/**
 * Origem do lançamento financeiro no ConsumoEsperto.
 */
public enum OrigemTransacao {
    MANUAL,
    ANDROID_NOTIFICATION,
    IOS_WALLET,
    OPEN_FINANCE,
    WHATSAPP,
    FATURA_PDF,
    CSV_BANK_STATEMENT,
    CSV_CARD_STATEMENT,
    PIX,
    NFC_E,
    AGENDAMENTO,
    RECORRENCIA
}
