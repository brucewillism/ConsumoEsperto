package com.consumoesperto.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parâmetros extraídos para registro de empréstimo consignado já contratado. */
@Data
@Builder
public class PropostaEmprestimoConsignado {

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    private BigDecimal valorTomado;
    private Integer quantidadeParcelas;
    private BigDecimal valorParcela;
    private Long contaBancariaId;
    private String nomeConta;

    private static final Pattern VALOR_TOMADO_PATTERN = Pattern.compile(
        "(?i)(?:no\\s+)?valor\\s+(?:de\\s+)?(?:R\\$\\s*)?"
            + "(\\d{1,3}(?:\\.\\d{3})*,\\d{2}|\\d{1,3}(?:,\\d{3})*\\.\\d{2}|\\d+,\\d{2}|\\d+(?:\\.\\d{2})?)"
    );

    private static final Pattern PARCELA_X_PATTERN = Pattern.compile(
        "(?i)(\\d{1,3})\\s*(?:x|vezes)\\s*(?:de\\s*)?"
            + "(\\d{1,3}(?:\\.\\d{3})*,\\d{2}|\\d{1,3}(?:,\\d{3})*\\.\\d{2}|\\d+,\\d{2}|\\d+(?:\\.\\d{2})?)"
    );

    private static final Pattern PARCELAS_DE_PATTERN = Pattern.compile(
        "(?i)(\\d{1,3})\\s*parcelas?\\s*(?:de\\s*)?"
            + "(\\d{1,3}(?:\\.\\d{3})*,\\d{2}|\\d{1,3}(?:,\\d{3})*\\.\\d{2}|\\d+,\\d{2}|\\d+(?:\\.\\d{2})?)"
    );

    private static final Pattern CONTA_CAiu_PATTERN = Pattern.compile(
        "(?i)caiu\\s+(?:no|na)\\s+([\\p{L}][\\p{L}0-9 \\-]{1,35})"
    );

    private static final Pattern CONTA_EXPLICITa_PATTERN = Pattern.compile(
        "(?i)conta\\s+(?:do|da|de|no|na)\\s+([\\p{L}][\\p{L}0-9 \\-]{1,35})"
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

    /** Resumo com máscara BRL para eco/confirmacao no WhatsApp. */
    public String formatarResumoMonetario() {
        StringBuilder sb = new StringBuilder();
        if (valorTomado != null) {
            sb.append("*Valor tomado:* ").append(BRL.format(valorTomado));
        } else {
            sb.append("*Valor tomado:* _(não identificado)_");
        }
        if (quantidadeParcelas != null && valorParcela != null) {
            sb.append("\n*Parcelas:* ").append(quantidadeParcelas)
                .append("× ").append(BRL.format(valorParcela));
        } else if (quantidadeParcelas != null) {
            sb.append("\n*Parcelas:* ").append(quantidadeParcelas).append(" _(valor da parcela não identificado)_");
        } else {
            sb.append("\n*Parcelas:* _(não identificadas)_");
        }
        if (nomeContaValido(nomeConta)) {
            sb.append("\n*Conta:* ").append(nomeConta.trim());
        }
        return sb.toString();
    }

    private static void enriquecerDoTexto(PropostaEmprestimoConsignado p, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        // 1) Valores monetários primeiro — evita confundir "no valor de" com nome de conta
        if (p.getValorTomado() == null) {
            p.setValorTomado(extrairValorTomado(text));
        }
        if (p.getQuantidadeParcelas() == null || p.getValorParcela() == null) {
            extrairParcelas(text, p);
        }
        // 2) Conta só se mencionada explicitamente (caiu no Itaú / conta Nubank)
        if (!nomeContaValido(p.getNomeConta())) {
            p.setNomeConta(null);
            p.setNomeConta(extrairNomeConta(text));
        }
        if (!nomeContaValido(p.getNomeConta())) {
            p.setNomeConta(null);
        }
    }

    private static void extrairParcelas(String text, PropostaEmprestimoConsignado p) {
        Matcher m = PARCELA_X_PATTERN.matcher(text);
        boolean found = m.find();
        if (!found) {
            m = PARCELAS_DE_PATTERN.matcher(text);
            found = m.find();
        }
        if (!found) {
            return;
        }
        if (p.getQuantidadeParcelas() == null) {
            p.setQuantidadeParcelas(Integer.parseInt(m.group(1)));
        }
        if (p.getValorParcela() == null) {
            p.setValorParcela(parseMoney(m.group(2)));
        }
    }

    private static String extrairNomeConta(String text) {
        Matcher m = CONTA_CAiu_PATTERN.matcher(text);
        if (m.find()) {
            return limparNomeConta(m.group(1));
        }
        m = CONTA_EXPLICITa_PATTERN.matcher(text);
        if (m.find()) {
            return limparNomeConta(m.group(1));
        }
        return null;
    }

    private static String limparNomeConta(String raw) {
        if (raw == null) {
            return null;
        }
        String n = raw.trim();
        String lower = n.toLowerCase(Locale.ROOT);
        for (String stop : new String[] {" com ", " parcela", " em ", " no valor", " valor "}) {
            int idx = lower.indexOf(stop);
            if (idx > 0) {
                n = n.substring(0, idx).trim();
                lower = n.toLowerCase(Locale.ROOT);
            }
        }
        return n.isBlank() ? null : n;
    }

    static boolean nomeContaValido(String nome) {
        if (nome == null || nome.isBlank()) {
            return false;
        }
        String n = nome.toLowerCase(Locale.ROOT).trim();
        if (n.contains("valor") || n.contains("parcela") || n.contains("consignado") || n.contains("emprestimo")) {
            return false;
        }
        if (n.matches(".*\\d.*")) {
            return false;
        }
        if (n.startsWith("de ") || n.equals("de")) {
            return false;
        }
        return n.length() >= 2;
    }

    private static BigDecimal extrairValorTomado(String text) {
        Matcher m = VALOR_TOMADO_PATTERN.matcher(text);
        if (m.find()) {
            return parseMoney(m.group(1));
        }
        m = Pattern.compile(
            "(?i)(?:de|consignado|emprestimo|empréstimo)\\s+(?:de\\s+)?"
                + "(\\d{1,3}(?:\\.\\d{3})*,\\d{2}|\\d{1,3}(?:,\\d{3})*\\.\\d{2}|\\d+,\\d{2}|\\d+(?:\\.\\d{2})?)\\s*(?:k|mil)?"
        ).matcher(text);
        if (m.find()) {
            return parseMoney(m.group(1));
        }
        m = Pattern.compile("(?i)(\\d+(?:[.,]\\d{2})?)\\s*(?:k|mil)\\b").matcher(text);
        if (m.find()) {
            BigDecimal v = parseMoney(m.group(1));
            if (v != null) {
                return v.multiply(BigDecimal.valueOf(1000)).setScale(2, java.math.RoundingMode.HALF_UP);
            }
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
