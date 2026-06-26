package com.consumoesperto.service;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Heurísticas para distinguir pagamento em conta/PIX vs cartão no WhatsApp.
 * Evita falso positivo quando «pix» faz parte do nome do estabelecimento (ex.: «Pix Prime Gestão Ltda»).
 */
public final class WhatsappPaymentMethodHeuristics {

    private WhatsappPaymentMethodHeuristics() {}

    public static boolean indicaCartaoExplicito(String paymentMethodJson, String sourceText) {
        if (paymentMethodJson != null && !paymentMethodJson.isBlank()) {
            String pm = paymentMethodJson.trim();
            if ("CARTAO".equalsIgnoreCase(pm) || "CARTÃO".equalsIgnoreCase(pm)
                || "CARD".equalsIgnoreCase(pm) || "CREDIT_CARD".equalsIgnoreCase(pm)) {
                return true;
            }
        }
        if (sourceText == null || sourceText.isBlank()) {
            return false;
        }
        String t = normalize(sourceText);
        return t.contains("cartao") || t.contains("cartão")
            || t.contains("credito") || t.contains("crédito");
    }

    public static boolean indicaPagamentoEmConta(String sourceText) {
        if (indicaCartaoExplicito(null, sourceText)) {
            return false;
        }
        if (sourceText == null || sourceText.isBlank()) {
            return false;
        }
        String t = normalize(sourceText);
        if (indicaPixComoMeioPagamento(t)) {
            return true;
        }
        if (t.contains("ted") || t.contains(" doc ") || t.startsWith("doc ")
            || t.contains("transferencia") || t.contains("transferência")
            || t.contains("debito em conta") || t.contains("débito em conta")
            || t.contains("debito na conta") || t.contains("débito na conta")) {
            return true;
        }
        return t.contains("na conta") || t.contains("da conta") || t.contains("em conta")
            || t.contains("conta corrente") || t.contains("conta bancaria") || t.contains("conta bancária");
    }

    static boolean indicaPixComoMeioPagamento(String normalizedText) {
        if (normalizedText == null || !normalizedText.contains("pix")) {
            return false;
        }
        if (pixPareceNomeEstabelecimento(normalizedText)) {
            return false;
        }
        return normalizedText.contains("via pix") || normalizedText.contains("por pix")
            || normalizedText.contains("no pix") || normalizedText.contains("em pix")
            || normalizedText.contains("pagamento pix") || normalizedText.contains("paguei pix")
            || normalizedText.contains("transferencia pix") || normalizedText.contains("transferência pix")
            || normalizedText.contains("pix instantaneo") || normalizedText.contains("pix instantâneo");
    }

    private static boolean pixPareceNomeEstabelecimento(String t) {
        if (t.contains("nome pix") || t.contains("com o nome pix") || t.contains("chamad") && t.contains("pix")) {
            return true;
        }
        if (t.contains(" ltda") || t.contains(" sa ") || t.contains(" epp") || t.contains(" me ")) {
            if (t.contains("pix")) {
                return true;
            }
        }
        return t.matches(".*\\bpix\\s+(prime|pay|pag|bank|gestao|gestão|shop|store|market|express|holding|servicos|serviços).*");
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
    }
}
