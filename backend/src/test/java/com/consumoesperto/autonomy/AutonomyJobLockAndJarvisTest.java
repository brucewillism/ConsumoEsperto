package com.consumoesperto.autonomy;

import com.consumoesperto.model.AutonomyJobLock;
import com.consumoesperto.repository.AutonomyJobLockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AutonomyJobLockAndJarvisTest {

    @Mock private AutonomyJobLockRepository lockRepository;
    @Mock private AutonomyPreferenciaService preferenciaService;
    @Mock private JarvisNotificationChannel channel;

    private final AtomicReference<AutonomyJobLock> stored = new AtomicReference<>();

    @BeforeEach
    void stubLock() {
        stored.set(null);
        when(lockRepository.findById(any())).thenAnswer(inv -> Optional.ofNullable(stored.get()));
        when(lockRepository.saveAndFlush(any(AutonomyJobLock.class))).thenAnswer(inv -> {
            AutonomyJobLock lock = inv.getArgument(0);
            stored.set(lock);
            return lock;
        });
        when(lockRepository.renew(any(), any(), any())).thenAnswer(inv -> {
            AutonomyJobLock lock = stored.get();
            String lockedBy = inv.getArgument(2);
            if (lock != null && lockedBy.equals(lock.getLockedBy())
                && lock.getLockedUntil() != null && lock.getLockedUntil().isAfter(LocalDateTime.now())) {
                lock.setLockedUntil(inv.getArgument(1));
                return 1;
            }
            return 0;
        });
    }

    @Test
    void segundoNoNaoRoubaLockVigente() {
        when(lockRepository.stealExpired(any(), any(), any(), any())).thenReturn(0);
        AutonomyJobLockService a = new AutonomyJobLockService(lockRepository);
        AutonomyJobLockService b = new AutonomyJobLockService(lockRepository);
        assertTrue(a.tryAcquire("job", Duration.ofMinutes(10)));
        stored.get().setLockedUntil(LocalDateTime.now().plusMinutes(10));
        assertFalse(b.tryAcquire("job", Duration.ofMinutes(10)));
        assertTrue(a.tryAcquire("job", Duration.ofMinutes(10)));
    }

    @Test
    void lockExpiradoOutroNoAssume() {
        AutonomyJobLock existing = new AutonomyJobLock();
        existing.setJobName("job");
        existing.setLockedBy("dead-node");
        existing.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        stored.set(existing);
        when(lockRepository.stealExpired(any(), any(), any(), any())).thenReturn(1);
        AutonomyJobLockService svc = new AutonomyJobLockService(lockRepository);
        assertTrue(svc.tryAcquire("job", Duration.ofMinutes(10)));
    }

    @Test
    void quietHoursSeguraInfoMasNaoCritical() {
        com.consumoesperto.config.FinancialAutonomyProperties props =
            new com.consumoesperto.config.FinancialAutonomyProperties();
        props.setEnabled(true);
        props.setProactiveJarvis(true);
        com.consumoesperto.model.AutonomyPreferencia pref =
            new com.consumoesperto.model.AutonomyPreferencia();
        pref.setJarvisProativo(true);
        java.time.LocalTime now = com.consumoesperto.util.AppTimeZone.agora().toLocalTime();
        pref.setSilenciosoInicio(now.minusHours(1));
        pref.setSilenciosoFim(now.plusHours(1));
        when(preferenciaService.obterOuCriar(1L)).thenReturn(pref);
        when(channel.deliver(any(), any(), any(), any(), any(), any())).thenReturn(true);
        JarvisProactiveOrchestrator orch = new JarvisProactiveOrchestrator(
            props, preferenciaService, java.util.List.of(channel));
        assertFalse(orch.notify(1L, JarvisNotificationPriority.INFO, "t", "m"));
        assertTrue(orch.notify(1L, JarvisNotificationPriority.CRITICAL, "t", "m"));
    }
}
