package com.consumoesperto.service;

import com.consumoesperto.config.ForecastProjecaoConfig;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.TransacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComposicaoProjecaoMesServicePartesTest {

    @Mock private DespesaFixaService despesaFixaService;
    @Mock private TransacaoRepository transacaoRepository;
    @Mock private FaturaRepository faturaRepository;

    private ComposicaoProjecaoMesService service;

    @BeforeEach
    void setup() {
        ForecastProjecaoConfig cfg = new ForecastProjecaoConfig();
        service = new ComposicaoProjecaoMesService(
            despesaFixaService, transacaoRepository, faturaRepository, cfg);
    }

    @Test
    void partesSomamCategoriasDisjuntasIncluindoFolhaNaExibicao() {
        YearMonth ym = YearMonth.of(2026, 9);
        LocalDate ref = ym.atDay(10);
        when(despesaFixaService.somarValorRestanteNoMes(1L, ref)).thenReturn(new BigDecimal("100.00"));
        when(faturaRepository.sumValorFaturasPendentesNoMes(eq(1L), any(), any()))
            .thenReturn(new BigDecimal("300.00"));
        when(transacaoRepository.sumParcelasEmprestimoPrevistasNoMes(eq(1L), any(), any()))
            .thenReturn(new BigDecimal("80.00"));
        when(transacaoRepository.sumParcelasEmprestimoPrevistasNoMesTodas(eq(1L), any(), any()))
            .thenReturn(new BigDecimal("200.00"));

        var partes = service.partesObrigacoesMes(1L, ym, ref);
        assertEquals(0, new BigDecimal("120.00").compareTo(partes.parcelasEmprestimoFolha()));
        assertEquals(0, new BigDecimal("480.00").compareTo(partes.comprometidoDisjuntoCaixa()));
        assertEquals(0, new BigDecimal("600.00").compareTo(partes.comprometidoDisjuntoExibicao()));
    }
}
