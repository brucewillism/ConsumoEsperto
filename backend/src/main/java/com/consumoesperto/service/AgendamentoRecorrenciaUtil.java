package com.consumoesperto.service;

import com.consumoesperto.model.AgendamentoPagamento;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Cálculo de próxima execução para agendamentos recorrentes.
 * Regra mensal: dia configurado; se o mês não tiver o dia (ex.: 31 em fev), usa o último dia válido.
 */
public final class AgendamentoRecorrenciaUtil {

    private AgendamentoRecorrenciaUtil() {}

    public static LocalDate calcularProximaExecucao(
        LocalDate base,
        AgendamentoPagamento.RecorrenciaAgendamento recorrencia,
        Integer diaVencimentoMensal
    ) {
        if (base == null || recorrencia == null || recorrencia == AgendamentoPagamento.RecorrenciaAgendamento.UNICA) {
            return null;
        }
        return switch (recorrencia) {
            case DIARIA -> base.plusDays(1);
            case SEMANAL -> base.plusWeeks(1);
            case QUINZENAL -> base.plusDays(15);
            case MENSAL -> proximoMes(base, diaVencimentoMensal != null ? diaVencimentoMensal : base.getDayOfMonth());
            case BIMESTRAL -> proximoMes(base.plusMonths(1), diaVencimentoMensal != null ? diaVencimentoMensal : base.getDayOfMonth());
            case TRIMESTRAL -> proximoMes(base.plusMonths(2), diaVencimentoMensal != null ? diaVencimentoMensal : base.getDayOfMonth());
            case SEMESTRAL -> proximoMes(base.plusMonths(5), diaVencimentoMensal != null ? diaVencimentoMensal : base.getDayOfMonth());
            case ANUAL -> base.plusYears(1);
            default -> null;
        };
    }

    static LocalDate proximoMes(LocalDate aPartirDe, int diaDesejado) {
        YearMonth proximo = YearMonth.from(aPartirDe).plusMonths(1);
        int dia = VencimentoMensalUtil.diaEfetivoNoMes(diaDesejado, proximo.lengthOfMonth());
        return proximo.atDay(dia);
    }
}
