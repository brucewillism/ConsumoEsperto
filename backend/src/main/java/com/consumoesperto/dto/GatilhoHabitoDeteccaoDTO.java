package com.consumoesperto.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/** Resultado de {@link com.consumoesperto.service.CerebroSemanticoService#detectarGatilhoHabito} — efeito dominó na memória HABITO. */
@Value
@Builder
public class GatilhoHabitoDeteccaoDTO {
    String gatilhoRotulo;
    String alvoRotulo;
    int probabilidadePercentual;
    BigDecimal valorMedioSegundaPerna;
}
