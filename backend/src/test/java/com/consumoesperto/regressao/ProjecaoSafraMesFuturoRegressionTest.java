package com.consumoesperto.regressao;

import com.consumoesperto.config.ForecastProjecaoConfig;
import com.consumoesperto.dto.RendaConfigDTO;
import com.consumoesperto.model.TipoConfiguracaoRenda;
import com.consumoesperto.repository.ContaBancariaRepository;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.TransferenciaContaRepository;
import com.consumoesperto.service.ComposicaoProjecaoMesService;
import com.consumoesperto.service.ConciliacaoAuditoriaService;
import com.consumoesperto.service.ContaBancariaService;
import com.consumoesperto.service.DespesaFixaService;
import com.consumoesperto.service.OpenAiService;
import com.consumoesperto.service.PlanejamentoFiscalService;
import com.consumoesperto.service.RendaConfigService;
import com.consumoesperto.service.SaldoMovimentacaoService;
import com.consumoesperto.service.SaldoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Regressão PR-06: mês futuro da safra usa composição bottom-up (fixas + parcelas + faturas), não só burn diário.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjecaoSafraMesFuturoRegressionTest {

    @Mock private TransacaoRepository transacaoRepository;
    @Mock private FaturaRepository faturaRepository;
    @Mock private ContaBancariaRepository contaBancariaRepository;
    @Mock private TransferenciaContaRepository transferenciaContaRepository;
    @Mock private OpenAiService openAiService;
    @Mock private ContaBancariaService contaBancariaService;
    @Mock private SaldoMovimentacaoService saldoMovimentacaoService;
    @Mock private RendaConfigService rendaConfigService;
    @Mock private PlanejamentoFiscalService planejamentoFiscalService;
    @Mock private ConciliacaoAuditoriaService conciliacaoAuditoriaService;
    @Mock private DespesaFixaService despesaFixaService;
    @Mock private ForecastProjecaoConfig forecastProjecaoConfig;
    @Mock private ComposicaoProjecaoMesService composicaoProjecaoMesService;

    @InjectMocks private SaldoService saldoService;

    @Test
    void mesFuturo_incluiObrigacoesBottomUp() {
        Long usuarioId = 1L;
        when(contaBancariaRepository.findByUsuarioIdAndAtivaTrueOrderByPadraoDescNomeAsc(usuarioId))
            .thenReturn(java.util.List.of());
        when(transacaoRepository.sumValorConfirmadaByUsuarioIdAndTipoTransacao(any(), any()))
            .thenReturn(BigDecimal.ZERO);
        when(transacaoRepository.sumDespesaConfirmadaCaixaPorUsuarioId(usuarioId)).thenReturn(BigDecimal.ZERO);
        when(transacaoRepository.sumPassivoEmprestimoVincendas(eq(usuarioId), any(LocalDateTime.class)))
            .thenReturn(BigDecimal.ZERO);

        when(forecastProjecaoConfig.getDiaLiminarAntiSusto()).thenReturn(15);
        when(forecastProjecaoConfig.getMargemVariavelPct()).thenReturn(new BigDecimal("10"));

        when(transacaoRepository.sumConfirmadaByUsuarioIdAndTipoAndPeriodo(any(), any(), any(), any()))
            .thenReturn(new BigDecimal("300.00"));
        when(rendaConfigService.getRendaMensalEstimada(usuarioId)).thenReturn(new BigDecimal("5000.00"));
        RendaConfigDTO cfg = new RendaConfigDTO();
        cfg.setTipoConfiguracaoRenda(TipoConfiguracaoRenda.CONTRACHEQUE);
        cfg.setDiaPagamento(5);
        when(rendaConfigService.obterDto(usuarioId)).thenReturn(Optional.of(cfg));
        when(transacaoRepository.sumReceitaSalarialConfirmadaPeriodo(any(), any(), any()))
            .thenReturn(BigDecimal.ZERO);
        when(planejamentoFiscalService.somarReceitasPrevistasNoMes(any(), any())).thenReturn(BigDecimal.ZERO);
        when(conciliacaoAuditoriaService.receitasFiscaisLiquidasNoMes(any(), any(), any()))
            .thenReturn(BigDecimal.ZERO);
        when(transacaoRepository.sumReceitaDecimoTerceiroPrevistaPeriodo(any(), any(), any()))
            .thenReturn(BigDecimal.ZERO);
        when(transacaoRepository.sumReceitaDecimoTerceiroConfirmadaPeriodo(any(), any(), any()))
            .thenReturn(BigDecimal.ZERO);

        when(composicaoProjecaoMesService.comporDespesasPrevistasMes(
            eq(usuarioId), any(YearMonth.class), any(LocalDate.class), any(BigDecimal.class)))
            .thenReturn(new BigDecimal("2500.00"));

        var safra = saldoService.calcularProjecaoSafra(usuarioId, 1);
        var mesFuturo = safra.meses().get(1);

        assertTrue(mesFuturo.despesasPrevistas().compareTo(new BigDecimal("2000.00")) >= 0,
            "mês futuro deve refletir obrigações bottom-up (2.5k), não só burn de ~30/dia × 30");
    }
}
