package com.consumoesperto.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parâmetros extraídos para registro de empréstimo consignado já contratado. */
@Data
@Builder
public class PropostaEmprestimoConsignado {

    private BigDecimal valorTomado;
    private Integer quantidadeParcelas;
    private BigDecimal valorParcela;
    private Long contaBancariaId;
    private String nomeConta;

    private static final Pattern PARCELA_PATTERN = Pattern.compile(
        "(?i)(\\d{1,3})\\s*(?:x|vezes)\\s*(?:de\\s*)?(\\d{1,3}(?:[.,]\\d{3})*(?:[.,]\\d{2})?|\\d+(?:[.,]\\d{2})?)"
    );

    public static PropostaEmprestimoConsignado fromComando(JsonNode cmd, String sourceText) {
        PropostaEmprestimoConsignado.PropostaEmprestimoConsignadoBuilder b = PropostaEmprestimoConsignado.builder();
        b.valorTomado(firstMoney(cmd, "valorTomado", "valorTotal", "amount"));
        b.valorParcela(firstMoney(cmd, "valorParcela", "installmentAmount"));
        b.quantidadeParcelas(readInt(cmd, "quantidadeParcelas", "installmentCount"));
        Long contaId = readLong(cmd, "contaBancariaId");
        if (contaId == null) {
            contaId = readLong(cmd, "accountId");
        }
        b.contaBancariaId(contaId);
        b.nomeConta(firstNonBlank(
            cmd.path("nomeConta").asText(null),
            cmd.path("accountName").asText(null),
            cmd.path("bank").asText(null)
        ));
        PropostaEmprestimoConsignado p = b.build();
        enriquecerDoTexto(p, sourceText);
        return p;
    }

    public boolean temMinimoParaCalcular() {
        return valorTomado != null && valorTomado.compareTo(BigDecimal.ZERO) > 0
            && quantidadeParcelas != null && quantidadeParcelas > 0;
    }

    private static void enriquecerDoTexto(PropostaEmprestimoConsignado p, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (p.getValorTomado() == null) {
            p.setValorTomado(extrairValorTomado(text));
        }
        if (p.getQuantidadeParcelas() == null || p.getValorParcela() == null) {
            Matcher m = PARCELA_PATTERN.matcher(text);
            if (m.find()) {
                if (p.getQuantidadeParcelas() == null) {
                    p.setQuantidadeParcelas(Integer.parseInt(m.group(1)));
                }
                if (p.getValorParcela() == null) {
                    p.setValorParcela(parseMoney(m.group(2)));
                }
            }
        }
        if (p.getNomeConta() == null || p.getNomeConta().isBlank()) {
            p.setNomeConta(extrairNomeConta(text));
        }
    }

    private static String extrairNomeConta(String text) {
        Matcher m = Pattern.compile("(?i)(?:caiu\\s+(?:no|na)|conta\\s+(?:do|da|de)?\\s*|no\\s+|na\\s+)([\\p{L}0-9 ]{2,40})")
            .matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    private static BigDecimal extrairValorTomado(String text) {
        Matcher m = Pattern.compile("(?i)(?:de|consignado|emprestimo|empréstimo)\\s+(?:de\\s+)?(\\d{1,3}(?:[.,]\\d{3})*(?:[.,]\\d{2})?|\\d+(?:[.,]\\d{2})?|\\d+)\\s*(?:k|mil)?)")
            .matcher(text);
        if (m.find()) {
            return parseMoney(m.group(1));
        }
        m = Pattern.compile("(?i)(\\d{1,3}(?:[.,]\\d{3})*(?:[.,]\\d{2})?|\\d+(?:[.,]\\d{2})?)\\s*(?:k|mil)\\b").matcher(text);
        if (m.find()) {
            BigDecimal v = parseMoney(m.group(1));
            if (text.toLowerCase(Locale.ROOT).contains("mil") || text.toLowerCase(Locale.ROOT).contains("k")) {
                return v.multiply(BigDecimal.valueOf(1000));
            }
            return v;
        }
        return null;
    }

    private static BigDecimal firstMoney(JsonNode cmd, String... fields) {
        for (String f : fields) {
            JsonNode n = cmd.path(f);
            if (!n.isMissingNode() && !n.isNull()) {
                if (n.isNumber()) {
                    return n.decimalValue().setScale(2, java.math.RoundingMode.HALF_UP);
                }
                BigDecimal v = parseMoney(n.asText());
                if (v != null) {
                    return v;
                }
            }
        }
        return null;
    }

    private static Integer readInt(JsonNode cmd, String... fields) {
        for (String f : fields) {
            JsonNode n = cmd.path(f);
            if (!n.isMissingNode() && !n.isNull() && n.canConvertToInt()) {
                return n.asInt();
            }
        }
        return null;
    }

    private static Long readLong(JsonNode cmd, String field) {
        JsonNode n = cmd.path(field);
        if (!n.isMissingNode() && !n.isNull() && n.canConvertToLong()) {
            return n.asLong();
        }
        return null;
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    static BigDecimal parseMoney(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT).replace("r$", "").replace(" ", "");
        boolean mil = s.endsWith("k") || s.contains("mil");
        s = s.replace("mil", "").replace("k", "");
        if (s.contains(",") && s.contains(".")) {
            s = s.replace(".", "").replace(",", ".");
        } else if (s.contains(",")) {
            s = s.replace(",", ".");
        }
        try {
            BigDecimal v = new BigDecimal(s).setScale(2, java.math.RoundingMode.HALF_UP);
            return mil ? v.multiply(BigDecimal.valueOf(1000)).setScale(2, java.math.RoundingMode.HALF_UP) : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
