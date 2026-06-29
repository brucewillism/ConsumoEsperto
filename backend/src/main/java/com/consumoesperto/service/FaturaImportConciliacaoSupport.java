package com.consumoesperto.service;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import com.consumoesperto.model.Fatura;
import com.consumoesperto.model.Transacao;

import java.time.YearMonth;

/**
 * Regras de conciliação na importação de PDF — evita roubar parcelas de PREVISTA futuras.
 */
final class FaturaImportConciliacaoSupport {

    private FaturaImportConciliacaoSupport() {}

    static boolean deveVincularTransacaoExistenteNaFatura(
        Transacao tx,
        Fatura faturaAlvo,
        ImportacaoFaturaItemDTO item
    ) {
        if (tx == null || faturaAlvo == null) {
            return false;
        }
        Fatura faturaTx = tx.getFatura();
        if (faturaTx == null || faturaTx.getId() == null) {
            return true;
        }
        if (faturaAlvo.getId() != null && faturaAlvo.getId().equals(faturaTx.getId())) {
            return true;
        }

        Fatura.StatusFatura statusTx = faturaTx.getStatusFatura();
        if (statusTx == Fatura.StatusFatura.PAGA || statusTx == Fatura.StatusFatura.CANCELADA) {
            return false;
        }

        YearMonth ymAlvo = mesVencimento(faturaAlvo);
        YearMonth ymTx = mesVencimento(faturaTx);
        if (ymAlvo == null || ymTx == null) {
            return statusTx != Fatura.StatusFatura.PREVISTA;
        }

        if (statusTx == Fatura.StatusFatura.PREVISTA && ymTx.isAfter(ymAlvo)) {
            return false;
        }

        if (statusTx == Fatura.StatusFatura.PREVISTA
            && ymTx.equals(ymAlvo)
            && item != null
            && item.getParcelaAtual() != null
            && tx.getParcelaAtual() != null
            && !item.getParcelaAtual().equals(tx.getParcelaAtual())) {
            return false;
        }

        if (!ymTx.equals(ymAlvo) && statusTx != Fatura.StatusFatura.PREVISTA) {
            return false;
        }

        return true;
    }

    private static YearMonth mesVencimento(Fatura f) {
        if (f == null || f.getDataVencimento() == null) {
            return null;
        }
        return YearMonth.from(f.getDataVencimento());
    }
}
