package com.consumoesperto.service.motor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Metas com probabilidade, ritmo e recomendações derivadas de números reais.
 */
public final class MetaInteligenteEngine {

    public record MetaResultado(
        Long metaId,
        String descricao,
        int probabilidadeSucessoPct,
        BigDecimal ritmoAtualMensal,
        BigDecimal ritmoNecessarioMensal,
        BigDecimal diferencaMensal,
        String recomendacaoDeterministica
    ) {}

    private MetaInteligenteEngine() {}

    public static List<MetaResultado> analisar(
        MotorFinanceiroSnapshot s,
        BigDecimal gastoLazerMensalMedio
    ) {
        List<MetaResultado> out = new ArrayList<>();
        if (s.metas() == null) {
            return out;
        }
        for (MotorFinanceiroSnapshot.MetaSnapshot m : s.metas()) {
            out.add(analisarMeta(m, gastoLazerMensalMedio));
        }
        return out;
    }

    private static MetaResultado analisarMeta(
        MotorFinanceiroSnapshot.MetaSnapshot m,
        BigDecimal gastoLazer
    ) {
        BigDecimal alvo = nz(m.valorAlvo());
        BigDecimal acum = nz(m.valorAcumulado());
        LocalDate hoje = LocalDate.now();
        long mesesDecorridos = Math.max(1,
            ChronoUnit.MONTHS.between(m.dataCriacao().withDayOfMonth(1), hoje.withDayOfMonth(1)) + 1);
        long mesesRestantes = Math.max(1,
            ChronoUnit.MONTHS.between(hoje, m.dataAlvo()));

        BigDecimal ritmoAtual = acum.divide(BigDecimal.valueOf(mesesDecorridos), 2, RoundingMode.HALF_UP);
        BigDecimal faltante = alvo.subtract(acum).max(BigDecimal.ZERO);
        BigDecimal ritmoNecessario = faltante.divide(BigDecimal.valueOf(mesesRestantes), 2, RoundingMode.HALF_UP);
        BigDecimal diferenca = ritmoNecessario.subtract(ritmoAtual).max(BigDecimal.ZERO);

        BigDecimal projecao = acum.add(ritmoAtual.multiply(BigDecimal.valueOf(mesesRestantes)));
        int prob = alvo.compareTo(BigDecimal.ZERO) <= 0 ? 0
            : projecao.multiply(BigDecimal.valueOf(100))
                .divide(alvo, 0, RoundingMode.HALF_UP)
                .intValue();
        prob = Math.max(0, Math.min(100, prob));

        String rec = recomendacao(m, prob, diferenca, gastoLazer, ritmoAtual, ritmoNecessario);
        return new MetaResultado(m.id(), m.descricao(), prob, ritmoAtual, ritmoNecessario, diferenca, rec);
    }

    private static String recomendacao(
        MotorFinanceiroSnapshot.MetaSnapshot m,
        int probAtual,
        BigDecimal diferenca,
        BigDecimal gastoLazer,
        BigDecimal ritmoAtual,
        BigDecimal ritmoNecessario
    ) {
        if (probAtual >= 95) {
            return "Meta no ritmo — mantenha aportes de R$ "
                + ritmoAtual.setScale(2, RoundingMode.HALF_UP) + "/mês.";
        }
        if (diferenca.compareTo(BigDecimal.ZERO) <= 0) {
            return "Ritmo atual (R$ " + ritmoAtual.setScale(2, RoundingMode.HALF_UP)
                + "/mês) cobre a meta no prazo.";
        }
        if (gastoLazer != null && gastoLazer.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal pct = diferenca.multiply(BigDecimal.valueOf(100))
                .divide(gastoLazer, 0, RoundingMode.HALF_UP);
            if (pct.compareTo(BigDecimal.valueOf(100)) <= 0 && pct.compareTo(BigDecimal.ZERO) > 0) {
                int probNova = Math.min(99, probAtual + pct.intValue() / 2);
                return "Se direcionar " + pct + "% dos gastos de lazer (média R$ "
                    + gastoLazer.setScale(2, RoundingMode.HALF_UP) + "/mês) para «" + m.descricao()
                    + "», a previsão passa de " + probAtual + "% para cerca de " + probNova + "%.";
            }
        }
        return "Aumente aportes em R$ " + diferenca.setScale(2, RoundingMode.HALF_UP)
            + "/mês (necessário R$ " + ritmoNecessario.setScale(2, RoundingMode.HALF_UP)
            + " vs atual R$ " + ritmoAtual.setScale(2, RoundingMode.HALF_UP) + ").";
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
