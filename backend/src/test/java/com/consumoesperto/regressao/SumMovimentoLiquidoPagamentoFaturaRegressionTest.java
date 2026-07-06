package com.consumoesperto.regressao;

import com.consumoesperto.config.ForecastProjecaoConfig;
import com.consumoesperto.model.Transacao;
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
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** ST-09: movimento líquido inclui PAGAMENTO_FATURA via query dedicada. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SumMovimentoLiquidoPagamentoFaturaRegressionTest {

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
    void movimentoLiquido_delegaQueryComPagamentoFatura() {
        LocalDateTime ini = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime fim = LocalDateTime.of(2026, 1, 31, 23, 59);
        when(transacaoRepository.sumMovimentoLiquidoContaConfirmadaPeriodo(eq(1L), eq(ini), eq(fim)))
            .thenReturn(new BigDecimal("-500.00"));

        BigDecimal mov = saldoService.movimentoLiquidoContaConfirmadoPeriodo(1L, ini, fim);

        assertEquals(0, mov.compareTo(new BigDecimal("-500.00")));
    }
}
