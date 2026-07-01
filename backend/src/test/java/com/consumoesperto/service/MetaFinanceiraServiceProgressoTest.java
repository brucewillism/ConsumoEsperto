package com.consumoesperto.service;

import com.consumoesperto.model.MetaFinanceira;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetaFinanceiraServiceProgressoTest {

    @Test
    void progresso_porValorAcumulado_naoPorTempo() throws Exception {
        MetaFinanceiraService service = new MetaFinanceiraService(null, null, null, null);
        MetaFinanceira m = new MetaFinanceira();
        m.setValorTotal(new BigDecimal("10000.00"));
        m.setValorAcumulado(BigDecimal.ZERO);
        m.setPrazoMeses(new BigDecimal("10"));
        m.setDataCriacao(LocalDateTime.now().minusMonths(5));

        Method calc = MetaFinanceiraService.class.getDeclaredMethod("calcularProgressoPercentual", MetaFinanceira.class);
        calc.setAccessible(true);
        assertEquals(0, calc.invoke(service, m));

        m.setValorAcumulado(new BigDecimal("5000.00"));
        assertEquals(50, calc.invoke(service, m));
    }
}
