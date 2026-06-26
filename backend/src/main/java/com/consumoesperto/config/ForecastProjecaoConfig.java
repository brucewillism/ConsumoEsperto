package com.consumoesperto.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Parâmetros da projeção de fechamento do mês (forecast / J.A.R.V.I.S.).
 */
@Component
public class ForecastProjecaoConfig {

    @Value("${consumoesperto.forecast.dia-liminar-anti-susto:25}")
    private int diaLiminarAntiSusto;

    @Value("${consumoesperto.forecast.margem-variavel-pct:10}")
    private BigDecimal margemVariavelPct;

    public int getDiaLiminarAntiSusto() {
        return diaLiminarAntiSusto;
    }

    public BigDecimal getMargemVariavelPct() {
        return margemVariavelPct != null ? margemVariavelPct : BigDecimal.TEN;
    }
}
