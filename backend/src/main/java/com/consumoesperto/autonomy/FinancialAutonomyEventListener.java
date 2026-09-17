package com.consumoesperto.autonomy;

import com.consumoesperto.config.FinancialAutonomyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class FinancialAutonomyEventListener {

    private final FinancialAutonomyEngine engine;
    private final FinancialAutonomyProperties properties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("cerebroExecutor")
    public void onCommitted(FinancialAutonomySpringEvent event) {
        if (!properties.isEnabled() || event == null) {
            return;
        }
        try {
            engine.processEvent(event.getEventId());
        } catch (Exception e) {
            log.warn("autonomy_listener_failed eventId={}: {}", event.getEventId(), e.getMessage());
        }
    }
}
