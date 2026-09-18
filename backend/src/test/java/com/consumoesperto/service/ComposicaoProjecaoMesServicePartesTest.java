package com.consumoesperto.service;

import com.consumoesperto.config.ForecastProjecaoConfig;
import com.consumoesperto.model.Fatura;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        when(faturaRepository.findProximasNaoPagas(eq(1L), any(), any()))
            .thenReturn(List.of(fatura("300.00", null)));
        when(transacaoRepository.sumParcelasEmprestimoPrevistasNoMes(eq(1L), any(), any()))
            .thenReturn(new BigDecimal("80.00"));
        when(transacaoRepository.sumParcelasEmprestimoPrevistasNoMesTodas(eq(1L), any(), any()))
            .thenReturn(new BigDecimal("200.00"));

        var partes = service.partesObrigacoesMes(1L, ym, ref);
        assertEquals(0, new BigDecimal("120.00").compareTo(partes.parcelasEmprestimoFolha()));
        assertEquals(0, new BigDecimal("480.00").compareTo(partes.comprometidoDisjuntoCaixa()));
        assertEquals(0, new BigDecimal("600.00").compareTo(partes.comprometidoDisjuntoExibicao()));
    }

    @Test
    void aposDiaLiminarAindaIncluiParcelaQueDebitaContaENaoAFolha() {
        YearMonth ym = YearMonth.of(2026, 9);
        LocalDate ref = ym.atDay(20);
        when(despesaFixaService.somarValorRestanteNoMes(1L, ref)).thenReturn(new BigDecimal("100.00"));
        when(faturaRepository.findProximasNaoPagas(eq(1L), any(), any()))
            .thenReturn(List.of(fatura("200.00", null)));
        when(transacaoRepository.sumParcelasEmprestimoPrevistasNoMes(eq(1L), any(), any()))
            .thenReturn(new BigDecimal("80.00"));

        BigDecimal despesas = service.comporDespesasPrevistasMes(1L, ym, ref, new BigDecimal("10.00"));
        assertEquals(0, new BigDecimal("480.00").compareTo(despesas));
    }

    @Test
    void parcelaDeCartaoNaoDuplicaFatura() {
        YearMonth ym = YearMonth.of(2026, 9);
        LocalDate ref = ym.atDay(20);
        when(despesaFixaService.somarValorRestanteNoMes(1L, ref)).thenReturn(BigDecimal.ZERO.setScale(2));
        when(faturaRepository.findProximasNaoPagas(eq(1L), any(), any()))
            .thenReturn(List.of(faturaComParcelaNotebook()));
        when(transacaoRepository.sumParcelasEmprestimoPrevistasNoMes(eq(1L), any(), any()))
            .thenReturn(BigDecimal.ZERO.setScale(2));

        BigDecimal despesas = service.comporDespesasPrevistasMes(1L, ym, ref, BigDecimal.ZERO);
        assertEquals(0, new BigDecimal("2000.00").compareTo(despesas),
            "fatura 2000 já inclui parcela notebook 500; não pode dar 2500");
        verify(transacaoRepository, never()).sumParcelasEmprestimoPrevistasNoMesTodas(any(), any(), any());
    }

    @Test
    void faturaParcial_projetaRestante() {
        YearMonth ym = YearMonth.of(2026, 9);
        LocalDate ref = ym.atDay(20);
        when(despesaFixaService.somarValorRestanteNoMes(1L, ref)).thenReturn(BigDecimal.ZERO.setScale(2));
        when(faturaRepository.findProximasNaoPagas(eq(1L), any(), any()))
            .thenReturn(List.of(fatura("2000.00", "500.00")));
        when(transacaoRepository.sumParcelasEmprestimoPrevistasNoMes(eq(1L), any(), any()))
            .thenReturn(BigDecimal.ZERO.setScale(2));

        assertEquals(0, new BigDecimal("1500.00").compareTo(
            service.comporDespesasPrevistasMes(1L, ym, ref, BigDecimal.ZERO)));
    }

    @Test
    void faturaPagaNaoEntra_listaVazia() {
        YearMonth ym = YearMonth.of(2026, 9);
        LocalDate ref = ym.atDay(20);
        when(despesaFixaService.somarValorRestanteNoMes(1L, ref)).thenReturn(BigDecimal.ZERO.setScale(2));
        when(faturaRepository.findProximasNaoPagas(eq(1L), any(), any())).thenReturn(List.of());
        when(transacaoRepository.sumParcelasEmprestimoPrevistasNoMes(eq(1L), any(), any()))
            .thenReturn(BigDecimal.ZERO.setScale(2));

        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(
            service.comporDespesasPrevistasMes(1L, ym, ref, BigDecimal.ZERO)));
    }

    @Test
    void emprestimoMaisCartao_somamUmaVezCada() {
        YearMonth ym = YearMonth.of(2026, 9);
        LocalDate ref = ym.atDay(20);
        when(despesaFixaService.somarValorRestanteNoMes(1L, ref)).thenReturn(BigDecimal.ZERO.setScale(2));
        when(faturaRepository.findProximasNaoPagas(eq(1L), any(), any()))
            .thenReturn(List.of(fatura("2000.00", null)));
        when(transacaoRepository.sumParcelasEmprestimoPrevistasNoMes(eq(1L), any(), any()))
            .thenReturn(new BigDecimal("800.00"));

        assertEquals(0, new BigDecimal("2800.00").compareTo(
            service.comporDespesasPrevistasMes(1L, ym, ref, BigDecimal.ZERO)));
    }

    @Test
    void fixaInternet_agendamentoNaoEConsultado() {
        YearMonth ym = YearMonth.of(2026, 9);
        LocalDate ref = ym.atDay(10);
        when(despesaFixaService.somarValorRestanteNoMes(1L, ref)).thenReturn(new BigDecimal("120.00"));
        when(faturaRepository.findProximasNaoPagas(eq(1L), any(), any())).thenReturn(List.of());
        when(transacaoRepository.sumParcelasEmprestimoPrevistasNoMes(eq(1L), any(), any()))
            .thenReturn(BigDecimal.ZERO.setScale(2));
        when(transacaoRepository.sumParcelasEmprestimoPrevistasNoMesTodas(eq(1L), any(), any()))
            .thenReturn(BigDecimal.ZERO.setScale(2));

        var partes = service.partesObrigacoesMes(1L, ym, ref);
        assertEquals(0, new BigDecimal("120.00").compareTo(partes.fixas()));
        assertEquals(0, new BigDecimal("120.00").compareTo(partes.comprometidoDisjuntoCaixa()),
            "agendamento gerado pela fixa não entra no número — só a fixa");
    }

    private static Fatura fatura(String devido, String pago) {
        Fatura f = new Fatura();
        f.setValorFatura(new BigDecimal(devido));
        f.setValorTotal(new BigDecimal(devido));
        f.setValorPago(pago == null ? null : new BigDecimal(pago));
        f.setStatus(pago == null ? Fatura.StatusFatura.ABERTA : Fatura.StatusFatura.PARCIAL);
        return f;
    }

    private static Fatura faturaComParcelaNotebook() {
        Fatura f = fatura("2000.00", null);
        f.setNumeroFatura("2026-09");
        return f;
    }
}
