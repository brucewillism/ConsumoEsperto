package com.consumoesperto.service;

import com.consumoesperto.model.TipoConfiguracaoRenda;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fallback textual para configurar perfil de renda híbrida no WhatsApp.
 */
public final class IncomeProfileWhatsappTextParser {

    private IncomeProfileWhatsappTextParser() {}

    public static ObjectNode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim();
        TipoConfiguracaoRenda tipo = detectarTipo(t);
        if (tipo == null) {
            return null;
        }
        ObjectNode cmd = JsonNodeFactory.instance.objectNode();
        cmd.put("action", "SET_INCOME_PROFILE");
        cmd.put("tipoPerfil", tipo.name());
        cmd.put("confianca", 0.88);

        switch (tipo) {
            case FLUXO_DIARIO -> {
                BigDecimal meta = extrairValor(t, "(?i)meta\\s+(?:de\\s+)?(?:faturamento\\s+)?(?:em\\s+)?R?\\$?\\s*([0-9]+(?:[\\.,][0-9]+)?)");
                if (meta == null) {
                    meta = extrairValor(t, "(?i)(?:fluxo\\s+di[aá]rio|renda\\s+vari[aá]vel).*?R?\\$?\\s*([0-9]+(?:[\\.,][0-9]+)?)");
                }
                if (meta != null) {
                    cmd.put("metaFaturamentoMensal", meta);
                }
            }
            case RECEBIMENTO_UNICO -> {
                BigDecimal valor = extrairValor(t, "(?i)(?:pix\\s+)?[uú]nico\\s+(?:de\\s+)?R?\\$?\\s*([0-9]+(?:[\\.,][0-9]+)?)");
                if (valor == null) {
                    valor = extrairValor(t, "(?i)recebo\\s+(?:um\\s+)?(?:pix\\s+)?(?:de\\s+)?R?\\$?\\s*([0-9]+(?:[\\.,][0-9]+)?)");
                }
                if (valor != null) {
                    cmd.put("valorLiquidoFixo", valor);
                }
                Integer dia = extrairDia(t);
                if (dia != null) {
                    cmd.put("diaRecebimentoFixo", dia);
                }
            }
            case CONTRACHEQUE -> {
                BigDecimal bruto = extrairValor(t, "(?i)sal[aá]rio\\s+bruto\\s+(?:de\\s+)?(?:R?\\$?\\s*)?([0-9]+(?:[\\.,][0-9]+)?)");
                if (bruto == null) {
                    bruto = extrairValor(t, "(?i)bruto\\s+(?:de\\s+)?(?:R?\\$?\\s*)?([0-9]+(?:[\\.,][0-9]+)?)");
                }
                if (bruto != null) {
                    cmd.put("salarioBruto", bruto);
                }
                BigDecimal desc = extrairValor(t, "(?i)descontos?\\s+(?:de\\s+)?(?:R?\\$?\\s*)?([0-9]+(?:[\\.,][0-9]+)?)");
                if (desc != null) {
                    cmd.put("descontosHolerite", desc);
                }
                Integer dia = extrairDia(t);
                if (dia != null) {
                    cmd.put("diaRecebimento", dia);
                }
            }
        }
        return cmd;
    }

    private static TipoConfiguracaoRenda detectarTipo(String t) {
        String norm = t.toLowerCase();
        if (norm.contains("fluxo di") || norm.contains("múltiplos pix") || norm.contains("multiplos pix")
            || norm.contains("renda vari") || norm.contains("meta de faturamento") || norm.contains("minha renda agora é fluxo")) {
            return TipoConfiguracaoRenda.FLUXO_DIARIO;
        }
        if (norm.contains("pix único") || norm.contains("pix unico") || norm.contains("recebimento único")
            || norm.contains("recebimento unico") || norm.contains("recebo um pix")) {
            return TipoConfiguracaoRenda.RECEBIMENTO_UNICO;
        }
        if (norm.contains("contracheque") || norm.contains("holerite") || norm.contains("salário bruto")
            || norm.contains("salario bruto") || norm.contains("descontos de")) {
            return TipoConfiguracaoRenda.CONTRACHEQUE;
        }
        if (norm.contains("perfil de renda") || norm.contains("minha renda agora")) {
            return TipoConfiguracaoRenda.FLUXO_DIARIO;
        }
        return null;
    }

    private static BigDecimal extrairValor(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        if (!m.find()) {
            return null;
        }
        return parseMoney(m.group(1));
    }

    private static Integer extrairDia(String text) {
        Matcher m = Pattern.compile("(?i)(?:todo\\s+)?dia\\s*(\\d{1,2})").matcher(text);
        if (m.find()) {
            int d = Integer.parseInt(m.group(1));
            if (d >= 1 && d <= 31) {
                return d;
            }
        }
        return null;
    }

    private static BigDecimal parseMoney(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.replace("R$", "").trim();
        if (t.matches(".*\\d+[.,]\\d{3}([.,]\\d{2})?.*") || (t.contains(",") && t.lastIndexOf(',') > t.indexOf('.'))) {
            t = t.replace(".", "").replace(",", ".");
        } else {
            t = t.replace(",", ".");
        }
        try {
            return new BigDecimal(t.trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
