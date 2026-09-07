package com.consumoesperto.service.importacao.csv;

import com.consumoesperto.model.ImportedRowKind;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;

public final class FinancialCsvSemantics {

    private FinancialCsvSemantics() {}

    public static ImportedRowKind classify(
        String description,
        String typeColumn,
        BigDecimal signedAmount,
        boolean cardContext
    ) {
        String blob = norm(description) + " " + norm(typeColumn);
        if (parecePagamentoFatura(blob, cardContext)) {
            return ImportedRowKind.PAGAMENTO_FATURA;
        }
        if (pareceEstorno(blob)) {
            return ImportedRowKind.ESTORNO;
        }
        if (pareceTransferencia(blob)) {
            return ImportedRowKind.TRANSFERENCIA;
        }
        if (pareceReceita(blob, signedAmount, cardContext)) {
            return ImportedRowKind.RECEITA;
        }
        return ImportedRowKind.DESPESA;
    }

    static boolean parecePagamentoFatura(String blob, boolean cardContext) {
        if (blob.contains("pagamento recebido")
            || blob.contains("pagamento fatura")
            || blob.contains("pagto fatura")
            || blob.contains("pagamento de fatura")
            || blob.contains("fatura paga")
            || blob.contains("pagamento da fatura")
            || (blob.contains("pagamento") && blob.contains("cartao") && blob.contains("fatura"))) {
            return true;
        }
        return cardContext && blob.contains("pagamento") && (blob.contains("fatura") || blob.contains("bill payment"));
    }

    static boolean pareceEstorno(String blob) {
        return blob.contains("estorno")
            || blob.contains("cancelamento")
            || blob.contains("devolucao")
            || blob.contains("chargeback")
            || blob.contains("reversao")
            || blob.contains("credito contestacao");
    }

    static boolean pareceTransferencia(String blob) {
        return blob.contains(" ted ")
            || blob.startsWith("ted ")
            || blob.contains(" doc ")
            || blob.contains("transferencia")
            || blob.contains("tef ")
            || blob.contains("envio pix")
            || blob.contains("pix enviado")
            || blob.contains("transfer pix");
    }

    static boolean pareceReceita(String blob, BigDecimal signedAmount, boolean cardContext) {
        if (blob.contains("salario")
            || blob.contains("rendimento")
            || blob.contains("deposito")
            || blob.contains("pix recebido")
            || blob.contains("ted recebida")
            || blob.contains("credito em conta")) {
            return true;
        }
        if (cardContext) {
            return false;
        }
        return signedAmount != null && signedAmount.signum() > 0
            && (blob.contains("credito") || blob.contains("entrada"));
    }

    public static boolean valorEhDespesaNoCartao(ImportedRowKind kind, BigDecimal signedAmount) {
        if (kind == ImportedRowKind.ESTORNO || kind == ImportedRowKind.PAGAMENTO_FATURA) {
            return false;
        }
        if (kind == ImportedRowKind.RECEITA) {
            return false;
        }
        if (signedAmount == null) {
            return false;
        }
        // Cartão: lançamento positivo costuma ser compra; negativo pode ser crédito.
        return signedAmount.signum() != 0;
    }

    static String norm(String raw) {
        if (raw == null) {
            return "";
        }
        String n = Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
        return " " + n.replaceAll("\\s+", " ").trim() + " ";
    }
}
