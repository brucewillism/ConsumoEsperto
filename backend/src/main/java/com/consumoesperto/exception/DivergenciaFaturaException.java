package com.consumoesperto.exception;

/**
 * Sinaliza que a soma dos lançamentos selecionados não bate com o total da fatura.
 * Não é um erro fatal: o utilizador pode optar por confirmar mesmo assim
 * (ex.: extração incompleta que ele completa manualmente depois).
 */
public class DivergenciaFaturaException extends RuntimeException {
    public DivergenciaFaturaException(String message) {
        super(message);
    }
}
