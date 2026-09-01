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
    PIX,
    NFC_E,
    AGENDAMENTO,
    RECORRENCIA
}
