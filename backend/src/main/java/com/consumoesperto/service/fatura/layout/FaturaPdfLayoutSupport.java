package com.consumoesperto.service.fatura.layout;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

public final class FaturaPdfLayoutSupport {

    private FaturaPdfLayoutSupport() {
    }

    public static String norm(String raw) {
        if (raw == null) {
            return "";
        }
        return Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9 ]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    public static boolean contem(String textoNorm, String... tokens) {
        if (textoNorm == null || textoNorm.isBlank() || tokens == null) {
            return false;
        }
        for (String token : tokens) {
            if (token != null && !token.isBlank() && textoNorm.contains(norm(token))) {
                return true;
            }
        }
        return false;
    }

    public static boolean pareceFaturaCartao(String textoNorm) {
        return contem(
            textoNorm,
            "fatura",
            "vencimento",
            "pagamento minimo",
            "data de vencimento",
            "fechamento da fatura",
            "total da fatura",
            "total para pagamento",
            "demonstrativo de fatura",
            "lancamentos no cartao",
            "lancamentos do cartao",
            "transacoes de",
            "movimentacoes na fatura",
            "compras e saques"
        );
    }

    /** Valores placeholder da IA (schema JSON) não identificam o emissor. */
    public static boolean bancoExtraidoUtil(String banco) {
        if (banco == null || banco.isBlank()) {
            return false;
        }
        String n = norm(banco);
        if (n.length() < 2) {
            return false;
        }
        return switch (n) {
            case "...", "na", "n a", "n d", "desconhecido", "nao identificado", "nao informado",
                 "cartao", "banco", "emissor", "credito", "debito", "visa", "mastercard", "elo", "amex" -> false;
            default -> !n.matches("^\\.+$");
        };
    }

    /** Infere o banco emissor a partir do texto bruto do PDF quando a IA não preencheu bancoCartao. */
    public static String inferirBancoEmissorDoTexto(String textoNorm) {
        if (textoNorm == null || textoNorm.isBlank()) {
            return "";
        }
        if (contem(textoNorm, "itau", "itaú unibanco", "itaucard", "www itau com br", "cartao itau")) {
            return "Itaú";
        }
        if (contem(textoNorm, "nubank", "nu pagamentos", "nu bank")) {
            return "Nubank";
        }
        if (contem(textoNorm, "banco inter", "inter medium", "inter gold")) {
            return "Inter";
        }
        if (contem(textoNorm, "mercado pago", "mercadopago")) {
            return "Mercado Pago";
        }
        if (contem(textoNorm, "banco do brasil", "banco brasil")) {
            return "Banco do Brasil";
        }
        if (contem(textoNorm, "bradesco")) {
            return "Bradesco";
        }
        if (contem(textoNorm, "santander")) {
            return "Santander";
        }
        if (contem(textoNorm, "c6 bank", "c6bank")) {
            return "C6 Bank";
        }
        if (contem(textoNorm, "caixa economica", "cef ", "cartao caixa")) {
            return "Caixa";
        }
        if (contem(textoNorm, "xp investimentos", "cartao xp")) {
            return "XP";
        }
        if (contem(textoNorm, "banco do nordeste", "bnb ")) {
            return "Banco do Nordeste";
        }
        return "";
    }

    /** Modo de leitura detectado automaticamente a partir do PDF. */
    public enum SituacaoLeituraFaturaPdf {
        /** Fatura ainda em aberto — total do PDF é referência para conciliação. */
        ABERTA,
        /** Fatura já quitada no banco — total zerado; valor = soma dos lançamentos. */
        PAGA_NO_BANCO
    }

    /**
     * Detecta se o PDF é de fatura em aberto ou já paga no banco (total R$ 0,00).
     */
    public static SituacaoLeituraFaturaPdf detectarSituacaoLeituraFatura(String texto, BigDecimal valorTotalPdf) {
        if (pareceFaturaPagaNoTexto(texto)) {
            return SituacaoLeituraFaturaPdf.PAGA_NO_BANCO;
        }
        if (valorTotalPdf != null && valorTotalPdf.compareTo(BigDecimal.ZERO) > 0) {
            return SituacaoLeituraFaturaPdf.ABERTA;
        }
        if (texto != null && texto.matches(
            "(?is).*(?:valor da fatura|total desta fatura|total para pagamento)[^\\d]{0,80}R\\$\\s*0[,.]00.*"
        )) {
            return SituacaoLeituraFaturaPdf.PAGA_NO_BANCO;
        }
        return SituacaoLeituraFaturaPdf.ABERTA;
    }

    /** Fatura já paga — total R$ 0,00 no PDF; lançamentos costumam vir após o resumo. */
    public static boolean pareceFaturaPagaNoTexto(String texto) {
        if (texto == null || texto.isBlank()) {
            return false;
        }
        String n = norm(texto);
        if (contem(n, "fatura paga", "pagamento efetuado", "fatura quitada")) {
            return true;
        }
        boolean totalZerado = texto.matches(
            "(?is).*(?:valor da fatura|total desta fatura)[^\\d]{0,80}R\\$\\s*0[,.]00.*"
        );
        return totalZerado && contem(n, "fatura paga", "pagamento efetuado", "fatura quitada");
    }

    /** Um item genérico da IA («Lançamento da fatura») indica falha na extração detalhada. */
    public static boolean pareceDescricaoGenericaIa(String descricao) {
        if (descricao == null || descricao.isBlank()) {
            return true;
        }
        String n = norm(descricao);
        return n.contains("lancamento da fatura")
            || n.equals("lancamento")
            || n.contains("despesa fatura")
            || n.contains("despesa no cartao")
            || n.length() < 5;
    }

    /** Um item genérico da IA («Lançamento da fatura») indica falha na extração detalhada. */
    public static boolean pareceListaGenericaIa(List<ImportacaoFaturaItemDTO> itens) {
        if (itens == null || itens.isEmpty()) {
            return false;
        }
        if (itens.size() == 1) {
            return pareceDescricaoGenericaIa(itens.get(0).getDescricao());
        }
        long genericos = itens.stream()
            .filter(i -> pareceDescricaoGenericaIa(i.getDescricao()))
            .count();
        return genericos >= itens.size() || genericos > 0;
    }
}
