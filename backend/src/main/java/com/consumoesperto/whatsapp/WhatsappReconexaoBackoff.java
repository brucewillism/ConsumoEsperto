package com.consumoesperto.whatsapp;

import java.time.Duration;

/**
 * Backoff exponencial por degraus (1, 2, 5, 15, 30 min) — sem apagar a instância Evolution.
 */
public final class WhatsappReconexaoBackoff {

    private WhatsappReconexaoBackoff() {}

    /**
     * Espera até à próxima tentativa automática depois de {@code tentativasJaFeitas} falhas.
     * A primeira tentativa (0 falhas) é imediata.
     */
    public static Duration esperaAposFalhas(int tentativasJaFeitas, int[] backoffMinutos) {
        if (tentativasJaFeitas <= 0) {
            return Duration.ZERO;
        }
        int[] steps = (backoffMinutos == null || backoffMinutos.length == 0)
            ? new int[] {1, 2, 5, 15, 30}
            : backoffMinutos;
        int idx = Math.min(tentativasJaFeitas - 1, steps.length - 1);
        int minutes = Math.max(1, steps[idx]);
        return Duration.ofMinutes(minutes);
    }
}
