package com.consumoesperto.fiscal;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Valida integridade estrutural dos JSONs fiscais antes do uso.
 */
public final class FiscalTabelaSchemaValidator {

    private FiscalTabelaSchemaValidator() {}

    public static void validarArquivo(JsonNode root, int anoEsperado) {
        if (!root.has("ano") || root.get("ano").asInt() != anoEsperado) {
            throw new IllegalStateException("Ano divergente no JSON fiscal: esperado " + anoEsperado);
        }
        if (!root.path("versoes").isArray() || root.path("versoes").isEmpty()) {
            throw new IllegalStateException("Versões fiscais ausentes para ano " + anoEsperado);
        }
        List<Vigencia> vigencias = new ArrayList<>();
        for (JsonNode v : root.path("versoes")) {
            validarVersao(v, anoEsperado, vigencias);
        }
    }

    private static void validarVersao(JsonNode v, int ano, List<Vigencia> vigencias) {
        LocalDate inicio = LocalDate.parse(v.path("inicioVigencia").asText());
        LocalDate fim = v.hasNonNull("fimVigencia") ? LocalDate.parse(v.path("fimVigencia").asText()) : null;
        if (fim != null && inicio.isAfter(fim)) {
            throw new IllegalStateException("Início posterior ao fim na vigência " + inicio);
        }
        for (Vigencia existente : vigencias) {
            if (sobrepoe(inicio, fim, existente.inicio, existente.fim)) {
                throw new IllegalStateException("Vigências sobrepostas: " + inicio + " e " + existente.inicio);
            }
        }
        vigencias.add(new Vigencia(inicio, fim));

        JsonNode inss = v.path("inss");
        if (inss.isMissingNode()) {
            throw new IllegalStateException("Bloco INSS ausente");
        }
        BigDecimal teto = decimalObrigatorio(inss.path("teto"), "teto INSS");
        if (teto.signum() <= 0) {
            throw new IllegalStateException("Teto INSS inválido");
        }
        validarFaixasInss(inss.path("faixas"), teto);

        JsonNode irrf = v.path("irrf");
        validarFaixasIr(irrf.path("faixas"));
    }

    private static void validarFaixasInss(JsonNode arr, BigDecimal teto) {
        if (!arr.isArray() || arr.isEmpty()) {
            throw new IllegalStateException("Faixas INSS ausentes");
        }
        BigDecimal anterior = BigDecimal.ZERO;
        for (JsonNode n : arr) {
            BigDecimal limite = decimalObrigatorio(n.path("limiteSuperior"), "limite INSS");
            BigDecimal aliq = decimalObrigatorio(n.path("aliquotaPct"), "alíquota INSS");
            if (aliq.signum() < 0) {
                throw new IllegalStateException("Alíquota INSS negativa");
            }
            if (limite.compareTo(anterior) <= 0) {
                throw new IllegalStateException("Faixas INSS sobrepostas ou sem cobertura");
            }
            anterior = limite;
        }
        if (anterior.compareTo(teto) != 0) {
            throw new IllegalStateException("Última faixa INSS deve coincidir com o teto");
        }
    }

    private static void validarFaixasIr(JsonNode arr) {
        if (!arr.isArray() || arr.isEmpty()) {
            throw new IllegalStateException("Faixas IRRF ausentes");
        }
        BigDecimal anterior = BigDecimal.ZERO;
        for (int i = 0; i < arr.size(); i++) {
            JsonNode n = arr.get(i);
            BigDecimal aliq = decimalObrigatorio(n.path("aliquotaPct"), "alíquota IRRF");
            if (aliq.signum() < 0) {
                throw new IllegalStateException("Alíquota IRRF negativa");
            }
            JsonNode limNode = n.get("limiteSuperior");
            if (i < arr.size() - 1) {
                BigDecimal limite = decimalObrigatorio(limNode, "limite IRRF");
                if (limite.compareTo(anterior) <= 0) {
                    throw new IllegalStateException("Faixas IRRF inválidas");
                }
                anterior = limite;
            }
        }
    }

    private static BigDecimal decimalObrigatorio(JsonNode n, String campo) {
        if (n == null || n.isMissingNode() || n.isNull()) {
            throw new IllegalStateException("Campo monetário ausente: " + campo);
        }
        return n.decimalValue();
    }

    private static boolean sobrepoe(LocalDate aIni, LocalDate aFim, LocalDate bIni, LocalDate bFim) {
        LocalDate aEnd = aFim != null ? aFim : LocalDate.MAX;
        LocalDate bEnd = bFim != null ? bFim : LocalDate.MAX;
        return !aIni.isAfter(bEnd) && !bIni.isAfter(aEnd);
    }

    private record Vigencia(LocalDate inicio, LocalDate fim) {}
}
