package com.consumoesperto.service.fatura.layout;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

public final class FaturaPdfLayoutConciliacao {

    private FaturaPdfLayoutConciliacao() {
    }

    public static BigDecimal somaItens(List<ImportacaoFaturaItemDTO> itens) {
        if (itens == null || itens.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return itens.stream()
            .map(i -> i.getValor() != null ? i.getValor() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal tolerancia(BigDecimal valorTotal) {
        if (valorTotal == null || valorTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return new BigDecimal("5.00");
        }
        return valorTotal.multiply(new BigDecimal("0.02"))
            .setScale(2, RoundingMode.HALF_UP)
            .max(new BigDecimal("5.00"))
            .min(new BigDecimal("120.00"));
    }

    public static BigDecimal readMoney(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (node.isNumber()) {
            return node.decimalValue().setScale(2, RoundingMode.HALF_UP);
        }
        String raw = node.asText("").trim().replace("R$", "").replace(" ", "");
        if (raw.isBlank()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (raw.contains(",") && raw.contains(".")) {
            raw = raw.replace(".", "").replace(",", ".");
        } else if (raw.contains(",")) {
            raw = raw.replace(",", ".");
        }
        try {
            return new BigDecimal(raw).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
    }

    public static BigDecimal preferirSaldoFaturaAtual(
        JsonNode extracted,
        BigDecimal valorTotalPdf,
        List<ImportacaoFaturaItemDTO> itens,
        List<String> auditorias
    ) {
        if (valorTotalPdf == null || valorTotalPdf.compareTo(BigDecimal.ZERO) <= 0) {
            return valorTotalPdf;
        }
        BigDecimal soma = somaItens(itens);
        BigDecimal saldoAtual = readMoney(extracted.path("saldoFaturaAtual"));
        if (saldoAtual.compareTo(BigDecimal.ZERO) <= 0) {
            return valorTotalPdf;
        }
        BigDecimal tol = tolerancia(valorTotalPdf.max(saldoAtual));
        BigDecimal diffTotal = soma.subtract(valorTotalPdf).abs();
        BigDecimal diffAtual = soma.subtract(saldoAtual).abs();
        if (diffAtual.compareTo(tol) <= 0 && diffAtual.compareTo(diffTotal) < 0) {
            auditorias.add(
                "Conciliação alinhada ao saldo desta fatura (R$ " + formatBrl(saldoAtual).trim()
                    + "). O total a pagar no PDF (R$ " + formatBrl(valorTotalPdf).trim()
                    + ") pode incluir saldo anterior ou outros ajustes."
            );
            return saldoAtual;
        }
        return valorTotalPdf;
    }

    public static String formatBrl(BigDecimal v) {
        if (v == null) {
            return " —";
        }
        return new DecimalFormat("#,##0.00", new DecimalFormatSymbols(new Locale("pt", "BR"))).format(v);
    }
}
