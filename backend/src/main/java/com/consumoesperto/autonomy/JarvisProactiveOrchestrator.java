package com.consumoesperto.autonomy;

import com.consumoesperto.config.FinancialAutonomyProperties;
import com.consumoesperto.model.AutonomyPreferencia;
import com.consumoesperto.util.AppTimeZone;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class JarvisProactiveOrchestrator {

    private final FinancialAutonomyProperties properties;
    private final AutonomyPreferenciaService preferenciaService;
    private final List<JarvisNotificationChannel> channels;

    public boolean notify(
        Long usuarioId,
        JarvisNotificationPriority priority,
        String title,
        String message
    ) {
        return notify(usuarioId, priority, title, message, null, null);
    }

    public boolean notify(
        Long usuarioId,
        JarvisNotificationPriority priority,
        String title,
        String message,
        Long financialEventId,
        Long resourceId
    ) {
        if (!properties.isEnabled() || !properties.isProactiveJarvis()) {
            return false;
        }
        AutonomyPreferencia pref = preferenciaService.obterOuCriar(usuarioId);
        if (!pref.isJarvisProativo()
            && priority != JarvisNotificationPriority.CRITICAL
            && priority != JarvisNotificationPriority.ACTION_REQUIRED) {
            return false;
        }
        if (shouldHoldForQuietHours(pref, priority)) {
            log.debug("JARVIS quiet-hours hold userId={} priority={}", usuarioId, priority);
            return false;
        }
        boolean sent = false;
        for (JarvisNotificationChannel channel : channels) {
            try {
                sent = channel.deliver(usuarioId, title, message, priority, financialEventId, resourceId) || sent;
            } catch (Exception e) {
                log.debug("JARVIS canal {} falhou: {}", channel.name(), e.getMessage());
            }
        }
        return sent;
    }

    public void retryPendingDeliveries() {
        if (!properties.isEnabled() || !properties.isProactiveJarvis()) {
            return;
        }
        for (JarvisNotificationChannel channel : channels) {
            try {
                channel.retryPending();
            } catch (Exception e) {
                log.debug("JARVIS retry {}: {}", channel.name(), e.getMessage());
            }
        }
    }

    boolean shouldHoldForQuietHours(AutonomyPreferencia pref, JarvisNotificationPriority priority) {
        if (priority == JarvisNotificationPriority.CRITICAL) {
            return false;
        }
        LocalTime start = pref.getSilenciosoInicio();
        LocalTime end = pref.getSilenciosoFim();
        if (start == null) {
            start = LocalTime.parse(properties.getSilenciosoInicioDefault());
        }
        if (end == null) {
            end = LocalTime.parse(properties.getSilenciosoFimDefault());
        }
        return QuietHours.active(AppTimeZone.agora().toLocalTime(), start, end)
            && (priority == JarvisNotificationPriority.INFO
            || priority == JarvisNotificationPriority.NOTICE
            || priority == JarvisNotificationPriority.WARNING);
    }
}
