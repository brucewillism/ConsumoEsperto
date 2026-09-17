package com.consumoesperto.autonomy;

import com.consumoesperto.model.AutonomyJobLock;
import com.consumoesperto.repository.AutonomyJobLockRepository;
import com.consumoesperto.util.AppTimeZone;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lock exclusivo por job. Dois processos: só um drena o mesmo lote.
 * O detentor pode renovar. Depois da expiração, outro nó assume (steal atómico).
 */
@Service
@RequiredArgsConstructor
public class AutonomyJobLockService {

    private final AutonomyJobLockRepository repository;
    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);

    public String instanceId() {
        return instanceId;
    }

    @Transactional
    public boolean tryAcquire(String jobName, Duration ttl) {
        LocalDateTime now = AppTimeZone.agora();
        LocalDateTime until = now.plus(ttl);
        if (repository.renew(jobName, until, instanceId) == 1) {
            return true;
        }
        if (repository.findById(jobName).isEmpty()) {
            try {
                AutonomyJobLock lock = new AutonomyJobLock();
                lock.setJobName(jobName);
                lock.setLockedUntil(until);
                lock.setLockedBy(instanceId);
                repository.saveAndFlush(lock);
                return true;
            } catch (DataIntegrityViolationException e) {
                return steal(jobName, until, now);
            }
        }
        return steal(jobName, until, now);
    }

    private boolean steal(String jobName, LocalDateTime until, LocalDateTime now) {
        return repository.stealExpired(jobName, until, instanceId, now) == 1;
    }
}
