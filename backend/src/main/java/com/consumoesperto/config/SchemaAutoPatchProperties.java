package com.consumoesperto.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "consumoesperto.schema.autopatch")
public class SchemaAutoPatchProperties {

    /** Quando false, SchemaAutoPatchService nao altera o schema (Flyway e fonte da verdade). */
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
