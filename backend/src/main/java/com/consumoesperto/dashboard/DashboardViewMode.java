package com.consumoesperto.dashboard;

/**
 * Modo de visão do dashboard — resolvido no backend, não escondendo cards no cliente.
 */
public enum DashboardViewMode {
    MONTHLY,
    GENERAL;

    public static DashboardViewMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return GENERAL;
        }
        try {
            return DashboardViewMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return GENERAL;
        }
    }
}
