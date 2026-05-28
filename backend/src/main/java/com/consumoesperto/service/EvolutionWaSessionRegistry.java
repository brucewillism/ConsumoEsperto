package com.consumoesperto.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Quando o utilizador pede «Desligar Evolution», a API Evolution costuma manter
 * {@code connectionState: open} em cache mesmo após logout. Este registo faz a app
 * tratar a sessão como desligada até novo pareamento QR.
 */
@Component
public class EvolutionWaSessionRegistry {

    private final ConcurrentHashMap<Long, Long> suppressedUntilEpochMs = new ConcurrentHashMap<>();

    public void markUserDisconnected(Long usuarioId) {
        if (usuarioId == null) {
            return;
        }
        suppressedUntilEpochMs.put(usuarioId, Long.MAX_VALUE);
    }

    public void clearUserDisconnected(Long usuarioId) {
        if (usuarioId != null) {
            suppressedUntilEpochMs.remove(usuarioId);
        }
    }

    public boolean isUserDisconnected(Long usuarioId) {
        if (usuarioId == null) {
            return false;
        }
        Long until = suppressedUntilEpochMs.get(usuarioId);
        if (until == null) {
            return false;
        }
        if (until == Long.MAX_VALUE) {
            return true;
        }
        if (System.currentTimeMillis() > until) {
            suppressedUntilEpochMs.remove(usuarioId);
            return false;
        }
        return true;
    }
}
