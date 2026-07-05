package com.consumoesperto.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Reparo de dados financeiros corrompidos por bugs já corrigidos.
 * Fail-closed: com {@code enabled=false} (padrão) qualquer pedido de aplicação
 * devolve apenas o dry-run — nada é escrito.
 */
@Data
@Component
@ConfigurationProperties(prefix = "consumoesperto.saldo.reparo")
public class SaldoReparoProperties {

    /** Habilita a APLICAÇÃO do reparo (dry-run/relatório funcionam sempre). */
    private boolean enabled = false;
}
