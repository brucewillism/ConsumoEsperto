package com.consumoesperto.autonomy;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboxBackoffTest {

    @Test
    void primeiraTentativaCercaDeTrintaSegundos() {
        Duration d = OutboxBackoff.delayForAttempt(1, List.of(30, 120, 600, 1800), 0.0);
        assertTrue(d.getSeconds() >= 30);
        assertTrue(d.getSeconds() <= 36);
    }

    @Test
    void quartaTentativaUsaUltimoPatamar() {
        Duration d = OutboxBackoff.delayForAttempt(4, List.of(30, 120, 600, 1800), 0.0);
        assertTrue(d.getSeconds() >= 1800);
    }
}
