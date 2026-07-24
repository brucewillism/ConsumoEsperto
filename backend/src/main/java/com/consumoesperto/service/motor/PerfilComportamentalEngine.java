package com.consumoesperto.service.motor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Classificação comportamental por regras objetivas — sem IA.
 */
public final class PerfilComportamentalEngine {

    public enum Perfil {
        CONSERVADOR, EQUILIBRADO, IMPULSIVO, SAZONAL, RENDA_VARIAVEL
    }

    public record Resultado(Perfil perfil, int confiancaPct, Map<Perfil, Integer> pontuacaoPorPerfil) {}

    private PerfilComportamentalEngine() {}

    public static Resultado classificar(MotorFinanceiroSnapshot s) {
        Map<Perfil, Integer> scores = new LinkedHashMap<>();
        for (Perfil p : Perfil.values()) {
            scores.put(p, 0);
        }

        BigDecimal cvDespesa = coeficienteVariacao(s.despesasMensaisUltimos6());
        BigDecimal cvRenda = coeficienteVariacao(s.receitasMensaisUltimos6());
        BigDecimal tendenciaReserva = tendenciaPatrimonio(s.despesasMensaisUltimos6(), s.receitasMensaisUltimos6());

        // Conservador
        if (tendenciaReserva.compareTo(BigDecimal.ZERO) > 0) scores.merge(Perfil.CONSERVADOR, 25, Integer::sum);
        if (s.utilizacaoCreditoPct().compareTo(BigDecimal.valueOf(30)) <= 0) scores.merge(Perfil.CONSERVADOR, 20, Integer::sum);
        if (cvDespesa.compareTo(BigDecimal.valueOf(0.15)) <= 0) scores.merge(Perfil.CONSERVADOR, 20, Integer::sum);
        if (s.transacoesParceladas6m() <= 2) scores.merge(Perfil.CONSERVADOR, 15, Integer::sum);
        if (s.comprasForaOrcamento6m() <= 1) scores.merge(Perfil.CONSERVADOR, 10, Integer::sum);

        // Equilibrado
        if (s.orcamentosTotal() > 0) {
            BigDecimal pctVerde = BigDecimal.valueOf(s.orcamentosNoVerde())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(s.orcamentosTotal()), 2, RoundingMode.HALF_UP);
            if (pctVerde.compareTo(BigDecimal.valueOf(60)) >= 0) scores.merge(Perfil.EQUILIBRADO, 30, Integer::sum);
        }
        if (cvDespesa.compareTo(BigDecimal.valueOf(0.15)) > 0 && cvDespesa.compareTo(BigDecimal.valueOf(0.35)) <= 0) {
            scores.merge(Perfil.EQUILIBRADO, 20, Integer::sum);
        }
        if (s.utilizacaoCreditoPct().compareTo(BigDecimal.valueOf(30)) > 0
            && s.utilizacaoCreditoPct().compareTo(BigDecimal.valueOf(60)) <= 0) {
            scores.merge(Perfil.EQUILIBRADO, 15, Integer::sum);
        }

        // Impulsivo
        if (s.comprasForaOrcamento6m() >= 4) scores.merge(Perfil.IMPULSIVO, 25, Integer::sum);
        if (s.transacoesParceladas6m() >= 5) scores.merge(Perfil.IMPULSIVO, 20, Integer::sum);
        if (s.utilizacaoCreditoPct().compareTo(BigDecimal.valueOf(60)) > 0) scores.merge(Perfil.IMPULSIVO, 20, Integer::sum);
        if (saltoGastoRecente(s.despesasMensaisUltimos6())) scores.merge(Perfil.IMPULSIVO, 20, Integer::sum);

        // Sazonal
        if (cvDespesa.compareTo(BigDecimal.valueOf(0.35)) > 0) scores.merge(Perfil.SAZONAL, 30, Integer::sum);
        if (mesesExtremos(s.despesasMensaisUltimos6())) scores.merge(Perfil.SAZONAL, 25, Integer::sum);

        // Renda variável
        if (cvRenda.compareTo(BigDecimal.valueOf(0.25)) > 0) scores.merge(Perfil.RENDA_VARIAVEL, 35, Integer::sum);
        if (cvRenda.compareTo(BigDecimal.valueOf(0.40)) > 0) scores.merge(Perfil.RENDA_VARIAVEL, 20, Integer::sum);

        Perfil vencedor = scores.entrySet().stream()
            .max(Comparator.comparingInt(Map.Entry::getValue))
            .map(Map.Entry::getKey)
            .orElse(Perfil.EQUILIBRADO);

        int top = scores.get(vencedor);
        int segundo = scores.entrySet().stream()
            .filter(e -> e.getKey() != vencedor)
            .mapToInt(Map.Entry::getValue)
            .max()
            .orElse(0);
        int confianca = top == 0 ? 50 : Math.min(99, 55 + (top - segundo) * 3);

        return new Resultado(vencedor, confianca, scores);
    }

    private static BigDecimal coeficienteVariacao(java.util.List<BigDecimal> valores) {
        if (valores == null || valores.size() < 2) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal mean = sum.divide(BigDecimal.valueOf(valores.size()), 4, RoundingMode.HALF_UP);
        if (mean.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        double variance = 0;
        for (BigDecimal v : valores) {
            double d = v.subtract(mean).doubleValue();
            variance += d * d;
        }
        variance /= valores.size();
        double std = Math.sqrt(variance);
        return BigDecimal.valueOf(std / mean.doubleValue()).setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal tendenciaPatrimonio(java.util.List<BigDecimal> despesas, java.util.List<BigDecimal> receitas) {
        if (despesas == null || receitas == null || despesas.isEmpty()) {
            return BigDecimal.ZERO;
        }
        int n = Math.min(despesas.size(), receitas.size());
        if (n < 2) {
            return BigDecimal.ZERO;
        }
        BigDecimal saldoPrimeiro = receitas.get(0).subtract(despesas.get(0));
        BigDecimal saldoUltimo = receitas.get(n - 1).subtract(despesas.get(n - 1));
        return saldoUltimo.subtract(saldoPrimeiro);
    }

    private static boolean saltoGastoRecente(java.util.List<BigDecimal> despesas) {
        if (despesas == null || despesas.size() < 3) {
            return false;
        }
        int n = despesas.size();
        BigDecimal mediaAnterior = despesas.subList(0, n - 1).stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(n - 1), 2, RoundingMode.HALF_UP);
        if (mediaAnterior.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        BigDecimal ultimo = despesas.get(n - 1);
        return ultimo.compareTo(mediaAnterior.multiply(BigDecimal.valueOf(1.25))) > 0;
    }

    private static boolean mesesExtremos(java.util.List<BigDecimal> despesas) {
        if (despesas == null || despesas.size() < 3) {
            return false;
        }
        BigDecimal min = despesas.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal max = despesas.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        if (min.compareTo(BigDecimal.ZERO) <= 0) {
            return max.compareTo(BigDecimal.ZERO) > 0;
        }
        return max.divide(min, 2, RoundingMode.HALF_UP).compareTo(BigDecimal.valueOf(1.8)) >= 0;
    }
}
