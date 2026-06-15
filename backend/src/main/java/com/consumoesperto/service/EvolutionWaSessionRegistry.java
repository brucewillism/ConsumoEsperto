package com.consumoesperto.service;

import com.consumoesperto.model.UsuarioAiConfig;
import com.consumoesperto.repository.UsuarioAiConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Quando o utilizador pede «Desligar Evolution», a API Evolution costuma manter
 * {@code connectionState: open} em cache mesmo após logout. Este registo faz a app
 * tratar a sessão como desligada até novo pareamento QR.
 *
 * O estado persiste em {@link UsuarioAiConfig#evolutionSessionSuppressed} para sobreviver
 * a reinícios do backend.
 */
@Component
@RequiredArgsConstructor
public class EvolutionWaSessionRegistry {

    private final UsuarioAiConfigRepository usuarioAiConfigRepository;

    /** Evita apagar/recriar instância a cada 5 s no polling do modal QR. */
    private final ConcurrentHashMap<Long, Long> lastInstanceRecreateAtMs = new ConcurrentHashMap<>();

    @Transactional
    public void markUserDisconnected(Long usuarioId) {
        if (usuarioId == null) {
            return;
        }
        usuarioAiConfigRepository.findByUsuarioId(usuarioId).ifPresent(cfg -> {
            cfg.setEvolutionSessionSuppressed(true);
            usuarioAiConfigRepository.save(cfg);
        });
    }

    @Transactional
    public void clearUserDisconnected(Long usuarioId) {
        if (usuarioId == null) {
            return;
        }
        lastInstanceRecreateAtMs.remove(usuarioId);
        usuarioAiConfigRepository.findByUsuarioId(usuarioId).ifPresent(cfg -> {
            cfg.setEvolutionSessionSuppressed(false);
            usuarioAiConfigRepository.save(cfg);
        });
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

    @Transactional(readOnly = true)
    public boolean isUserDisconnected(Long usuarioId) {
        if (usuarioId == null) {
            return false;
        }
        return usuarioAiConfigRepository.findByUsuarioId(usuarioId)
            .map(UsuarioAiConfig::isEvolutionSessionSuppressed)
            .orElse(false);
    }
}
