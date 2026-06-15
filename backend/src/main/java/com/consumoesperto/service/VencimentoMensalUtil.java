package com.consumoesperto.service;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Cálculo de vencimento mensal (dia configurado vs. último dia do mês).
 */
public final class VencimentoMensalUtil {

    private VencimentoMensalUtil() {}

    /**
     * Verifica se o vencimento efetivo coincide com {@code hoje + diasAntecedencia},
     * respeitando meses curtos (ex.: dia 31 → 30 em abril, 28 em fevereiro).
     */
    public static boolean venceEmDias(Integer diaVencimento, LocalDate hoje, int diasAntecedencia) {
        if (diaVencimento == null || hoje == null || diasAntecedencia < 0) {
            return false;
        }
        LocalDate alvo = hoje.plusDays(diasAntecedencia);
        YearMonth ym = YearMonth.from(alvo);
        int efetivo = diaEfetivoNoMes(diaVencimento, ym.lengthOfMonth());
        return efetivo == alvo.getDayOfMonth();
    }

    public static int diaEfetivoNoMes(int diaVencimento, int ultimoDiaMes) {
        return Math.min(Math.max(1, diaVencimento), ultimoDiaMes);
    }
}
