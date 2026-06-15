package com.consumoesperto.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Mantém presença {@code unavailable} nas instâncias Evolution ligadas — evita status «online» permanente
 * e notificações silenciadas no telemóvel (comportamento conhecido do Baileys/Evolution).
 */
@Component
public class EvolutionPrivacyMaintenanceScheduler {

    private final EvolutionInstanceSettingsService evolutionInstanceSettingsService;

    @Value("${consumoesperto.evolution.privacy.presence-refresh-enabled:false}")
    private boolean presenceRefreshEnabled;

    @Value("${consumoesperto.evolution.session.sticky:true}")
    private boolean sessionSticky;

    public EvolutionPrivacyMaintenanceScheduler(EvolutionInstanceSettingsService evolutionInstanceSettingsService) {
        this.evolutionInstanceSettingsService = evolutionInstanceSettingsService;
    }

    @Scheduled(fixedDelayString = "${consumoesperto.evolution.privacy.presence-refresh-interval-ms:60000}")
    public void refreshPresenceForOpenInstances() {
        if (!presenceRefreshEnabled || sessionSticky) {
            return;
        }
        evolutionInstanceSettingsService.refreshPresenceForConnectedInstances();
    }
}
