package com.consumoesperto.autonomy;

public class FinancialAutonomySpringEvent {

    private final Long eventId;

    public FinancialAutonomySpringEvent(Long eventId) {
        this.eventId = eventId;
    }

    public Long getEventId() {
        return eventId;
    }
}
