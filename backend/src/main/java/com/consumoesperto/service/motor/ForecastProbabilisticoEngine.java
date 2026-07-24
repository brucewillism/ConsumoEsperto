package com.consumoesperto.service.motor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Probabilidades determinísticas — nunca aleatórias, sempre auditáveis.
 */
public final class ForecastProbabilisticoEngine {

    public record Resultado(
        int chanceMesPositivoPct,
        int chanceChequeEspecialPct,
        int chanceEstourarOrcamentoPct,
        String explicacaoDeterministica
    ) {}

    private ForecastProbabilisticoEngine() {}

    public static Resultado calcular(MotorFinanceiroSnapshot s) {
        int chancePositivo = chanceMesPositivo(s);
        int chanceCheque = chanceChequeEspecial(s);
        int chanceOrcamento = chanceEstourarOrcamento(s);
        String explicacao = montarExplicacao(s, chancePositivo, chanceCheque, chanceOrcamento);
        return new Resultado(chancePositivo, chanceCheque, chanceOrcamento, explicacao);
    }

    private static int chanceMesPositivo(MotorFinanceiroSnapshot s) {
        if (s.saldoProjetadoFimMes() == null) {
            return 50;
        }
        if (s.saldoProjetadoFimMes().compareTo(BigDecimal.ZERO) <= 0) {
            return clampPct(100 - margemNegativaPct(s));
        }
        BigDecimal renda = nz(s.rendaMensalMedia());
        if (renda.compareTo(BigDecimal.ZERO) <= 0) {
            return s.saldoProjetadoFimMes().compareTo(BigDecimal.ZERO) > 0 ? 75 : 25;
        }
        BigDecimal margem = s.saldoProjetadoFimMes()
            .multiply(BigDecimal.valueOf(100))
            .divide(renda, 2, RoundingMode.HALF_UP);
        int base = 50;
        if (margem.compareTo(BigDecimal.valueOf(10)) >= 0) base += 35;
        else if (margem.compareTo(BigDecimal.ZERO) >= 0) base += 20;
        else base -= 30;

        BigDecimal mediaDespesa = media(s.despesasMensaisUltimos6());
        if (mediaDespesa.compareTo(BigDecimal.ZERO) > 0 && s.gastoProjetadoMes() != null) {
            BigDecimal diffPct = mediaDespesa.subtract(s.gastoProjetadoMes())
                .multiply(BigDecimal.valueOf(100))
                .divide(mediaDespesa, 2, RoundingMode.HALF_UP);
            if (diffPct.compareTo(BigDecimal.valueOf(5)) >= 0) base += 10;
            if (diffPct.compareTo(BigDecimal.valueOf(-10)) <= 0) base -= 15;
        }
        return clampPct(base);
    }

    private static int chanceChequeEspecial(MotorFinanceiroSnapshot s) {
        if (s.saldoProjetadoFimMes() == null) {
            return 10;
        }
        if (s.saldoProjetadoFimMes().compareTo(BigDecimal.ZERO) < 0) {
            return clampPct(70 + margemNegativaPct(s) / 2);
        }
        BigDecimal saldo = nz(s.saldoContasDisponivel());
        BigDecimal burn = estimarBurnDiario(s);
        if (burn.compareTo(BigDecimal.ZERO) <= 0) {
            return 5;
        }
        BigDecimal diasAteZero = saldo.divide(burn, 0, RoundingMode.DOWN);
        if (diasAteZero.compareTo(BigDecimal.valueOf(7)) <= 0) return 55;
        if (diasAteZero.compareTo(BigDecimal.valueOf(15)) <= 0) return 30;
        if (diasAteZero.compareTo(BigDecimal.valueOf(30)) <= 0) return 15;
        return 5;
    }

    private static int chanceEstourarOrcamento(MotorFinanceiroSnapshot s) {
        if (s.orcamentosTotal() <= 0) {
            return 0;
        }
        int estourados = s.orcamentosEstourados();
        int emRisco = Math.max(0, s.orcamentosTotal() - s.orcamentosNoVerde() - estourados);
        int pct = (estourados * 100 + emRisco * 50) / s.orcamentosTotal();
        return clampPct(pct);
    }

    private static String montarExplicacao(
        MotorFinanceiroSnapshot s, int pos, int cheque, int orc
    ) {
        List<String> partes = new ArrayList<>();
        partes.add("Chance de fechar positivo: " + pos + "%.");
        BigDecimal mediaDesp = media(s.despesasMensaisUltimos6());
        if (mediaDesp.compareTo(BigDecimal.ZERO) > 0 && s.gastoProjetadoMes() != null) {
            BigDecimal diff = mediaDesp.subtract(s.gastoProjetadoMes())
                .multiply(BigDecimal.valueOf(100))
                .divide(mediaDesp, 0, RoundingMode.HALF_UP);
            if (diff.compareTo(BigDecimal.ZERO) >= 0) {
                partes.add("Despesas projetadas estão " + diff + "% abaixo da média dos últimos "
                    + s.despesasMensaisUltimos6().size() + " meses.");
            } else {
                partes.add("Despesas projetadas estão " + diff.abs() + "% acima da média recente.");
            }
        }
        if (s.faturasPendentesTotal() != null && s.faturasPendentesTotal().compareTo(BigDecimal.ZERO) > 0) {
            partes.add("Há faturas pendentes de R$ " + s.faturasPendentesTotal().setScale(2, RoundingMode.HALF_UP) + ".");
        } else {
            partes.add("Não há vencimentos extraordinários relevantes além do fluxo já projetado.");
        }
        if (cheque >= 30) {
            partes.add("Risco de cheque especial: " + cheque + "% (saldo ou projeção apertados).");
        }
        if (orc >= 40) {
            partes.add("Risco de estourar orçamento: " + orc + "%.");
        }
        return String.join(" ", partes);
    }

    private static BigDecimal estimarBurnDiario(MotorFinanceiroSnapshot s) {
        if (s.gastoProjetadoMes() == null) {
            return BigDecimal.ZERO;
        }
        return s.gastoProjetadoMes().divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);
    }

    private static int margemNegativaPct(MotorFinanceiroSnapshot s) {
        BigDecimal renda = nz(s.rendaMensalMedia());
        if (renda.compareTo(BigDecimal.ZERO) <= 0) {
            return 30;
        }
        return s.saldoProjetadoFimMes().abs()
            .multiply(BigDecimal.valueOf(100))
            .divide(renda, 0, RoundingMode.HALF_UP)
            .intValue();
    }

    private static BigDecimal media(java.util.List<BigDecimal> vals) {
        if (vals == null || vals.isEmpty()) return BigDecimal.ZERO;
        return vals.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(vals.size()), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static int clampPct(int v) {
        return Math.max(0, Math.min(100, v));
    }
}
