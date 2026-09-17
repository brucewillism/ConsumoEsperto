package com.consumoesperto.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "consumoesperto.dashboard.brief")
public class DashboardBriefProperties {

    /** Segunda 08:00 America/Sao_Paulo. */
    private String weeklyCron = "0 0 8 * * MON";
    /** Dia 28 08:00. */
    private String generalCron = "0 0 8 28 * *";
    private boolean weeklyEnabled = true;
    private boolean generalEnabled = true;
    /** Narrar via E.D.I.T.H.; números continuam determinísticos. */
    private boolean edithNarrate = true;
    private long edithTimeoutMs = 8_000L;
}
