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
    /** Evita apagar/recriar instância a cada 5 s no polling do modal QR. */
    private final ConcurrentHashMap<Long, Long> lastInstanceRecreateAtMs = new ConcurrentHashMap<>();

    public void markUserDisconnected(Long usuarioId) {
        if (usuarioId == null) {
            return;
        }
        suppressedUntilEpochMs.put(usuarioId, Long.MAX_VALUE);
    }

    public void clearUserDisconnected(Long usuarioId) {
        if (usuarioId != null) {
            suppressedUntilEpochMs.remove(usuarioId);
            lastInstanceRecreateAtMs.remove(usuarioId);
        }
    }

    /**
     * @return true se passou o cooldown desde o último delete+create para QR
     */
    public boolean tryAcquireInstanceRecreateForQr(Long usuarioId, long cooldownMs) {
        if (usuarioId == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        long cd = Math.max(5_000L, cooldownMs);
        final boolean[] allowed = { false };
        lastInstanceRecreateAtMs.compute(usuarioId, (id, prev) -> {
            if (prev == null || now - prev >= cd) {
                allowed[0] = true;
                return now;
            }
            return prev;
        });
        return allowed[0];
    }

    public void markInstanceRecreateDone(Long usuarioId) {
        if (usuarioId != null) {
            lastInstanceRecreateAtMs.put(usuarioId, System.currentTimeMillis());
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
