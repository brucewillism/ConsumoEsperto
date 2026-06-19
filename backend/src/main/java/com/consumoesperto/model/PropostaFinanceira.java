package com.consumoesperto.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parâmetros extraídos da intenção do usuário (IA ou heurística local).
 * Campos ausentes permanecem nulos — a calculadora trata cada cenário.
 */
@Data
@Builder
public class PropostaFinanceira {

    private TipoOperacaoFinanceira tipoOperacao;
    private BigDecimal valorTotal;
    private BigDecimal valorParcela;
    private Integer quantidadeParcelas;
    private String descricaoItem;

    private static final Pattern PARCELA_PATTERN = Pattern.compile(
        "(?i)(\\d{1,3})\\s*(?:x|vezes)\\s*(?:de\\s*)?(\\d{1,3}(?:[.,]\\d{3})*(?:[.,]\\d{2})?|\\d+(?:[.,]\\d{2})?)"
    );

    /** Monta a proposta a partir do JSON retornado pela IA ({@link com.consumoesperto.service.OpenAiService#parseCommand}). */
    public static PropostaFinanceira fromComando(JsonNode cmd, String sourceText) {
        if (cmd == null) {
            return fromTextoLivre(sourceText);
        }
        PropostaFinanceira.PropostaFinanceiraBuilder b = PropostaFinanceira.builder();
        b.tipoOperacao(resolverTipo(cmd.path("tipoOperacao").asText(null), sourceText));
        b.valorTotal(firstMoney(cmd, "valorTotal", "purchasePrice", "amount"));
        b.valorParcela(firstMoney(cmd, "valorParcela", "installmentAmount"));
        Integer parcelas = readInt(cmd, "quantidadeParcelas");
        if (parcelas == null) {
            parcelas = readInt(cmd, "installmentCount");
        }
        b.quantidadeParcelas(parcelas);
        String desc = firstNonBlank(
            cmd.path("descricaoItem").asText(null),
            cmd.path("description").asText(null)
        );
        b.descricaoItem(desc);

        PropostaFinanceira p = b.build();
        enriquecerComTexto(p, sourceText);
        inferirTipoSeNecessario(p, sourceText);
        return p;
    }

    /** Heurística local — usada pelo antigo oráculo de grande compra e fallbacks. */
    public static PropostaFinanceira fromTextoLivre(String sourceText) {
        PropostaFinanceira p = PropostaFinanceira.builder().build();
        enriquecerComTexto(p, sourceText);
        inferirTipoSeNecessario(p, sourceText);
        return p;
    }

    private static void enriquecerComTexto(PropostaFinanceira p, String sourceText) {
        if (sourceText == null || sourceText.isBlank()) {
            return;
        }
        Matcher m = PARCELA_PATTERN.matcher(sourceText);
        if (m.find()) {
            int n = Integer.parseInt(m.group(1));
            BigDecimal parcela = parseValorBr(m.group(2));
            if (n > 1 && parcela != null && parcela.compareTo(BigDecimal.ZERO) > 0) {
                if (p.getQuantidadeParcelas() == null) {
                    p.setQuantidadeParcelas(n);
                }
                if (p.getValorParcela() == null) {
                    p.setValorParcela(parcela);
                }
            }
        }
        if (p.getValorTotal() == null) {
            p.setValorTotal(extrairPrimeiroValorGrande(sourceText));
        }
        if (p.getDescricaoItem() == null || p.getDescricaoItem().isBlank()) {
            p.setDescricaoItem(extrairDescricaoItem(sourceText));
        }
    }

    private static void inferirTipoSeNecessario(PropostaFinanceira p, String sourceText) {
        if (p.getTipoOperacao() != null) {
            return;
        }
        String norm = normalizar(sourceText);
        if (norm.contains("consignado")) {
            p.setTipoOperacao(TipoOperacaoFinanceira.CONSIGNADO);
        } else if (norm.contains("emprestimo") || norm.contains("empréstimo")) {
            p.setTipoOperacao(TipoOperacaoFinanceira.EMPRESTIMO);
        } else if (norm.contains("financiamento") || norm.contains("financiar")) {
            p.setTipoOperacao(TipoOperacaoFinanceira.FINANCIAMENTO);
        } else if (ehParcelado(p)) {
            p.setTipoOperacao(TipoOperacaoFinanceira.COMPRA_PARCELADA);
        } else {
            p.setTipoOperacao(TipoOperacaoFinanceira.COMPRA_AVISTA);
        }
    }

    public static boolean ehParcelado(PropostaFinanceira p) {
        return p != null
            && p.getQuantidadeParcelas() != null
            && p.getQuantidadeParcelas() > 1
            && p.getValorParcela() != null
            && p.getValorParcela().compareTo(BigDecimal.ZERO) > 0;
    }

    private static TipoOperacaoFinanceira resolverTipo(String raw, String sourceText) {
        if (raw != null && !raw.isBlank()) {
            String norm = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
            try {
                return TipoOperacaoFinanceira.valueOf(norm);
            } catch (IllegalArgumentException ignored) {
                // inferir abaixo
            }
        }
        return null;
    }

    private static String extrairDescricaoItem(String text) {
        String norm = normalizar(text);
        String[] tokens = {"moto", "tenis", "tênis", "notebook", "iphone", "geladeira", "televisao", "televisão",
            "tv", "carro", "consignado", "emprestimo", "empréstimo", "financiamento"};
        for (String t : tokens) {
            if (norm.contains(normalizar(t))) {
                return t;
            }
        }
        return "compra";
    }

    private static BigDecimal extrairPrimeiroValorGrande(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher mil = Pattern.compile("(?i)(\\d+)\\s*(?:k|mil)\\b").matcher(raw);
        if (mil.find()) {
            return new BigDecimal(mil.group(1)).multiply(BigDecimal.valueOf(1000))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        }
        Pattern pat = Pattern.compile("(\\d{1,3}(?:\\.\\d{3})*(?:,\\d{2})|\\d+(?:,\\d{2})?)");
        Matcher m = pat.matcher(raw);
        if (m.find()) {
            return parseValorBr(m.group(1));
        }
        return null;
    }

    private static BigDecimal firstMoney(JsonNode cmd, String... fields) {
        for (String f : fields) {
            BigDecimal v = readMoney(cmd, f);
            if (v != null && v.compareTo(BigDecimal.ZERO) > 0) {
                return v;
            }
        }
        return null;
    }

    private static BigDecimal readMoney(JsonNode parent, String field) {
        if (parent == null || parent.isMissingNode()) {
            return null;
        }
        JsonNode n = parent.get(field);
        if (n == null || n.isNull()) {
            return null;
        }
        try {
            if (n.isNumber()) {
                return BigDecimal.valueOf(n.asDouble()).setScale(2, java.math.RoundingMode.HALF_UP);
            }
            return parseValorBr(n.asText(""));
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer readInt(JsonNode parent, String field) {
        if (parent == null || parent.isMissingNode()) {
            return null;
        }
        JsonNode n = parent.get(field);
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isInt()) {
            int v = n.asInt();
            return v > 0 ? v : null;
        }
        try {
            int v = Integer.parseInt(n.asText("").trim());
            return v > 0 ? v : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String firstNonBlank(String... xs) {
        if (xs == null) {
            return null;
        }
        for (String x : xs) {
            if (x != null && !x.isBlank()) {
                return x.trim();
            }
        }
        return null;
    }

    static BigDecimal parseValorBr(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String x = s.trim().replace("R$", "").trim();
        if (x.isEmpty()) {
            return null;
        }
        if (x.contains(",") && x.contains(".")) {
            x = x.replace(".", "").replace(",", ".");
        } else if (x.contains(",") && !x.contains(".")) {
            x = x.replace(",", ".");
        }
        try {
            return new BigDecimal(x).setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }

    private static String normalizar(String text) {
        if (text == null) {
            return "";
        }
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
    }
}
