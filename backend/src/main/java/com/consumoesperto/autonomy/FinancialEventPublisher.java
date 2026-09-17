package com.consumoesperto.autonomy;

import com.consumoesperto.config.FinancialAutonomyProperties;
import com.consumoesperto.model.FinancialDomainEvent;
import com.consumoesperto.repository.FinancialDomainEventRepository;
import com.consumoesperto.util.AppTimeZone;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialEventPublisher {

    private final FinancialAutonomyProperties properties;
    private final FinancialDomainEventRepository eventRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public Long publish(Long usuarioId, FinancialEventType type, Long aggregateId, String payloadMin) {
        if (!properties.isEnabled() || usuarioId == null || type == null) {
            return null;
        }
        FinancialDomainEvent row = new FinancialDomainEvent();
        row.setUsuarioId(usuarioId);
        row.setEventType(type.name());
        row.setAggregateId(aggregateId);
        row.setPayloadMin(trim(payloadMin, 500));
        row.setProcessed(false);
        row.setProcessingStatus(OutboxProcessingStatus.PENDING.name());
        row.setAttemptCount(0);
        row.setNextRetryAt(AppTimeZone.agora());
        row.setCreatedAt(AppTimeZone.agora());
        FinancialDomainEvent saved = eventRepository.save(row);
        applicationEventPublisher.publishEvent(new FinancialAutonomySpringEvent(saved.getId()));
        return saved.getId();
    }

    private static String trim(String v, int max) {
        if (v == null) {
            return null;
        }
        return v.length() <= max ? v : v.substring(0, max);
    }
}
