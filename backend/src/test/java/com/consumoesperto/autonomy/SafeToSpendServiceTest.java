package com.consumoesperto.autonomy;

import com.consumoesperto.config.FinancialAutonomyProperties;
import com.consumoesperto.dto.DisponibilidadeRealDTO;
import com.consumoesperto.service.PrevisaoFluxoCaixaService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SafeToSpendServiceTest {

    @Test
    void aplicaMargemSemIa() {
        PrevisaoFluxoCaixaService previsao = mock(PrevisaoFluxoCaixaService.class);
        DisponibilidadeRealDTO dto = new DisponibilidadeRealDTO();
        dto.setSaldoBancarioAtual(new BigDecimal("1000"));
        dto.setTotalObrigacoes(new BigDecimal("200"));
        dto.setDisponivelAposObrigacoes(new BigDecimal("800"));
        dto.setDiasRestantesNoMes(10);
        when(previsao.calcularDisponibilidadeReal(1L)).thenReturn(dto);
        FinancialAutonomyProperties props = new FinancialAutonomyProperties();
        props.setSafetyMargin(new BigDecimal("0.10"));
        Map<String, Object> out = new SafeToSpendService(previsao, props).calcular(1L);
        assertEquals(new BigDecimal("720.00"), out.get("safeToSpend"));
    }

    @Test
    void caixaSaudavelNaoFicaNegativoPorDividaTotalFutura() {
        PrevisaoFluxoCaixaService previsao = mock(PrevisaoFluxoCaixaService.class);
        DisponibilidadeRealDTO dto = new DisponibilidadeRealDTO();
        dto.setSaldoBancarioAtual(new BigDecimal("10000.00"));
        dto.setTotalObrigacoes(new BigDecimal("800.00"));
        dto.setDisponivelAposObrigacoes(new BigDecimal("9200.00"));
        dto.setDiasRestantesNoMes(12);
        when(previsao.calcularDisponibilidadeReal(1L)).thenReturn(dto);
        FinancialAutonomyProperties props = new FinancialAutonomyProperties();
        props.setSafetyMargin(BigDecimal.ZERO);
        Map<String, Object> out = new SafeToSpendService(previsao, props).calcular(1L);
        assertEquals(new BigDecimal("9200.00"), out.get("safeToSpend"));
        assertEquals(1, ((BigDecimal) out.get("safeToSpend")).signum());
    }
}
