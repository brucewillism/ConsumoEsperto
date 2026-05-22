package com.consumoesperto.dto;

import com.consumoesperto.service.AmortizacaoSazonalService;
import com.consumoesperto.service.SentinelaBufferSazonalService;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class RecalculoProjecaoSazonalDTO {
    private final BigDecimal patrimonioLiquido;
    private final SentinelaBufferSazonalService.ColchaoSazonal colchaoSazonal;
    private final List<AmortizacaoSazonalService.SimulacaoAntecipacao> oportunidadesAmortizacao;
}
