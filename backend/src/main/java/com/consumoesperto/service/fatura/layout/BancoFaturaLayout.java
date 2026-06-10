package com.consumoesperto.service.fatura.layout;

/**
 * Layout de fatura PDF reconhecido antes da extração pela IA.
 */
public enum BancoFaturaLayout {
    NUBANK("Nubank"),
    ITAU("Itaú"),
    INTER("Inter"),
    MERCADO_PAGO("Mercado Pago"),
    BANCO_BRASIL("Banco do Brasil"),
    BRADESCO("Bradesco"),
    SANTANDER("Santander"),
    CAIXA("Caixa"),
    C6_BANK("C6 Bank"),
    XP("XP Investimentos"),
    BANCO_NORDESTE("Banco do Nordeste"),
    MASTERCARD("Mastercard"),
    GENERICO("Genérico");

    private final String nomeExibicao;

    BancoFaturaLayout(String nomeExibicao) {
        this.nomeExibicao = nomeExibicao;
    }

    public String getNomeExibicao() {
        return nomeExibicao;
    }
}
