package com.consumoesperto.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;

/** Fuso horário oficial do app — alinhado aos jobs cron ({@code America/Sao_Paulo}). */
public final class AppTimeZone {

    public static final ZoneId BR = ZoneId.of("America/Sao_Paulo");

    private AppTimeZone() {}

    public static LocalDate hoje() {
        return LocalDate.now(BR);
    }

    public static LocalDateTime agora() {
        return LocalDateTime.now(BR);
    }

    public static YearMonth mesAtual() {
        return YearMonth.now(BR);
    }

    public static LocalDateTime inicioDoMes(YearMonth ym) {
        return ym.atDay(1).atStartOfDay();
    }

    public static LocalDateTime fimDoMes(YearMonth ym) {
        return ym.atEndOfMonth().atTime(23, 59, 59);
    }
}
