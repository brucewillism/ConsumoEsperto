package com.consumoesperto.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "consumoesperto.mobile-capture")
public class MobileCaptureProperties {

    private boolean enabled = false;
    private boolean androidEnabled = true;
    private boolean iosEnabled = true;
    private boolean edithClassificationEnabled = true;
    private String ingestionBaseUrl = "http://localhost:18081";
    private int rateLimitPerMinute = 60;
    private int maxPayloadBytes = 8192;
    private double autoCategoryThreshold = 0.90;
    private double suggestCategoryThreshold = 0.70;
    private int rawRetentionHours = 72;
    private boolean requireHttps = true;
    private int dedupWindowMinutes = 180;
}
