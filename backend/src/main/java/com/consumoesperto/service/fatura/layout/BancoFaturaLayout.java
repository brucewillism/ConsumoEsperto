package com.consumoesperto.service.fatura.layout;

/**
 * Layout de fatura PDF reconhecido antes da extração pela IA.
 */
public enum BancoFaturaLayout {
    NUBANK("Nubank"),
    ITAU("Itaú"),
    INTER("Inter"),
    MERCADO_PAGO("Mercado Pago"),
    MASTERCARD("Mastercard"),
    BANCO_BRASIL("Banco do Brasil"),
    GENERICO("Genérico");

    private final String nomeExibicao;

    BancoFaturaLayout(String nomeExibicao) {
        this.nomeExibicao = nomeExibicao;
    }

    public String getNomeExibicao() {
        return nomeExibicao;
    }
}
