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
        // «nome pix Fulano … no cartão X» = lançamento PIX no cartão de crédito, não débito em conta
        if (indicaPixBeneficiarioComCartao(sourceText)) {
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

    /**
     * Extrai apelido/banco após «no cartão …» / «cartão …» (ex.: «no cartao azul» → {@code azul}).
     */
    public static String extrairReferenciaCartaoDoTexto(String sourceText) {
        if (sourceText == null || sourceText.isBlank()) {
            return "";
        }
        String t = normalize(sourceText);
        java.util.regex.Matcher tail = java.util.regex.Pattern
            .compile("(?:no|em|do|da|pelo|pela)\\s+cart(?:ao|ão)\\s+(.+)$")
            .matcher(t);
        if (tail.find()) {
            return limparTokenCartao(tail.group(1));
        }
        java.util.regex.Matcher inline = java.util.regex.Pattern
            .compile("cart(?:ao|ão)\\s+(.+?)(?:\\s+em\\s+\\d+x|\\s+no\\s+valor|\\s+final\\s|$)")
            .matcher(t);
        if (inline.find()) {
            return limparTokenCartao(inline.group(1));
        }
        return "";
    }

    /** PIX a beneficiário (extrato) pago com cartão de crédito — não é PIX/conta corrente. */
    public static boolean indicaPixBeneficiarioComCartao(String sourceText) {
        if (sourceText == null || sourceText.isBlank()) {
            return false;
        }
        if (!indicaCartaoExplicito(null, sourceText)) {
            return false;
        }
        String t = normalize(sourceText);
        return pixPareceNomeEstabelecimento(t) || t.matches(".*\\bpix\\s+[a-z]{3,}.*");
    }

    private static String limparTokenCartao(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        s = s.replaceAll("\\s+(em|no|de|da|do|final|vence|limite)\\s+.*$", "").trim();
        if (s.length() > 48) {
            s = s.substring(0, 48).trim();
        }
        return s;
    }

    /**
     * A IA costuma colocar o beneficiário PIX em {@code cardName} (ex.: «pix pamela …») em vez do banco/apelido do cartão.
     */
    public static boolean cardNamePareceBeneficiarioPixOuDescricao(String cardName) {
        if (cardName == null || cardName.isBlank()) {
            return false;
        }
        String t = normalize(cardName);
        if (t.length() > 28) {
            return true;
        }
        if (pixPareceNomeEstabelecimento(t)) {
            return true;
        }
        return t.startsWith("pix ") || t.matches(".*\\bpix\\s+[a-z]{3,}.*");
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
        // Beneficiário PIX no extrato: «pix maria silva», «pix pamela …»
        if (t.matches(".*\\bpix\\s+[a-z]{3,}(\\s+[a-z]{2,}){0,8}.*")
            && !t.contains("via pix") && !t.contains("por pix") && !t.contains("no pix")
            && !t.contains("pagamento pix") && !t.contains("transferencia pix")) {
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
