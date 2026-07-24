package com.consumoesperto.service.motor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Score 0–100 auditável em 6 componentes fixos.
 */
public final class ScoreExplicavelEngine {

    public record Componente(String nome, int pontos, int maximo, String detalhe, String comoRecuperar) {}

    public record Resultado(int scoreTotal, List<Componente> componentes) {}

    private ScoreExplicavelEngine() {}

    public static Resultado calcular(MotorFinanceiroSnapshot s) {
        List<Componente> comps = new ArrayList<>();
        int reserva = pontosReserva(s);
        int orcamento = pontosOrcamento(s);
        int regularidade = pontosRegularidade(s);
        int credito = pontosCredito(s);
        int metas = pontosMetas(s);
        int comprometimento = pontosComprometimento(s);

        comps.add(new Componente("Reserva Financeira", reserva, 25,
            "Meses de reserva: " + fmt(s.mesesReserva()),
            dicaReserva(s)));
        comps.add(new Componente("Controle de Orçamento", orcamento, 20,
            s.orcamentosTotal() > 0
                ? s.orcamentosNoVerde() + " de " + s.orcamentosTotal() + " orçamentos no verde"
                : "Sem orçamentos cadastrados",
            "Cadastre orçamentos por categoria e mantenha uso abaixo de 80%."));
        comps.add(new Componente("Regularidade Financeira", regularidade, 15,
            "Volatilidade de gastos nos últimos meses",
            "Reduza picos de gasto e estabilize despesas discricionárias."));
        comps.add(new Componente("Uso de Crédito", credito, 20,
            "Utilização do limite: " + fmtPct(s.utilizacaoCreditoPct()) + "%",
            dicaCredito(s)));
        comps.add(new Componente("Saúde das Metas", metas, 10,
            "Progresso médio das metas: " + fmtPct(s.mediaProgressoMetasPct()) + "%",
            "Aumente aportes mensais nas metas prioritárias."));
        comps.add(new Componente("Comprometimento de Renda", comprometimento, 10,
            "Metas comprometem " + fmtPct(s.comprometimentoMetasPct()) + "% da renda",
            "Evite comprometer mais de 20% da renda com metas simultâneas."));

        int total = reserva + orcamento + regularidade + credito + metas + comprometimento;
        return new Resultado(Math.min(100, total), comps);
    }

    private static int pontosReserva(MotorFinanceiroSnapshot s) {
        BigDecimal m = s.mesesReserva() != null ? s.mesesReserva() : BigDecimal.ZERO;
        if (m.compareTo(BigDecimal.valueOf(6)) >= 0) return 25;
        if (m.compareTo(BigDecimal.valueOf(3)) >= 0) return 18;
        if (m.compareTo(BigDecimal.valueOf(1)) >= 0) return 10;
        if (m.compareTo(BigDecimal.ZERO) > 0) return 5;
        return 0;
    }

    private static int pontosOrcamento(MotorFinanceiroSnapshot s) {
        if (s.orcamentosTotal() <= 0) return 8;
        return (s.orcamentosNoVerde() * 20) / s.orcamentosTotal();
    }

    private static int pontosRegularidade(MotorFinanceiroSnapshot s) {
        BigDecimal cv = coefVar(s.despesasMensaisUltimos6());
        if (cv.compareTo(BigDecimal.valueOf(0.12)) <= 0) return 15;
        if (cv.compareTo(BigDecimal.valueOf(0.25)) <= 0) return 10;
        if (cv.compareTo(BigDecimal.valueOf(0.40)) <= 0) return 5;
        return 0;
    }

    private static int pontosCredito(MotorFinanceiroSnapshot s) {
        BigDecimal u = s.utilizacaoCreditoPct() != null ? s.utilizacaoCreditoPct() : BigDecimal.ZERO;
        if (u.compareTo(BigDecimal.valueOf(30)) <= 0) return 20;
        if (u.compareTo(BigDecimal.valueOf(50)) <= 0) return 14;
        if (u.compareTo(BigDecimal.valueOf(70)) <= 0) return 8;
        return 2;
    }

    private static int pontosMetas(MotorFinanceiroSnapshot s) {
        BigDecimal p = s.mediaProgressoMetasPct() != null ? s.mediaProgressoMetasPct() : BigDecimal.ZERO;
        return p.multiply(BigDecimal.valueOf(10))
            .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
            .intValue();
    }

    private static int pontosComprometimento(MotorFinanceiroSnapshot s) {
        BigDecimal c = s.comprometimentoMetasPct() != null ? s.comprometimentoMetasPct() : BigDecimal.ZERO;
        if (c.compareTo(BigDecimal.valueOf(15)) <= 0) return 10;
        if (c.compareTo(BigDecimal.valueOf(25)) <= 0) return 7;
        if (c.compareTo(BigDecimal.valueOf(40)) <= 0) return 4;
        return 0;
    }

    private static String dicaReserva(MotorFinanceiroSnapshot s) {
        BigDecimal m = s.mesesReserva() != null ? s.mesesReserva() : BigDecimal.ZERO;
        if (m.compareTo(BigDecimal.valueOf(3)) >= 0) return "Reserva adequada — mantenha o ritmo.";
        return "Construa reserva de pelo menos 3 meses de despesas.";
    }

    private static String dicaCredito(MotorFinanceiroSnapshot s) {
        BigDecimal u = s.utilizacaoCreditoPct() != null ? s.utilizacaoCreditoPct() : BigDecimal.ZERO;
        if (u.compareTo(BigDecimal.valueOf(30)) <= 0) return "Uso de crédito saudável.";
        int ganho = 20 - pontosCredito(s);
        if (ganho <= 0) return "Crédito sob controle.";
        return "Reduza utilização do cartão para abaixo de 30% do limite para recuperar até "
            + ganho + " ponto(s).";
    }

    private static BigDecimal coefVar(java.util.List<BigDecimal> vals) {
        if (vals == null || vals.size() < 2) return BigDecimal.ZERO;
        BigDecimal mean = vals.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(vals.size()), 4, RoundingMode.HALF_UP);
        if (mean.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        double var = 0;
        for (BigDecimal v : vals) {
            double d = v.subtract(mean).doubleValue();
            var += d * d;
        }
        var /= vals.size();
        return BigDecimal.valueOf(Math.sqrt(var) / mean.doubleValue()).setScale(4, RoundingMode.HALF_UP);
    }

    private static String fmt(BigDecimal v) {
        return v != null ? v.setScale(1, RoundingMode.HALF_UP).toPlainString() : "0";
    }

    private static String fmtPct(BigDecimal v) {
        return v != null ? v.setScale(0, RoundingMode.HALF_UP).toPlainString() : "0";
    }
}
