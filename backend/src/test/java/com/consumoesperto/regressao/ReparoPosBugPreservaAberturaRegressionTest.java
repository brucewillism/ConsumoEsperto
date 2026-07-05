package com.consumoesperto.regressao;

import com.consumoesperto.config.ForecastProjecaoConfig;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.ContaBancariaRepository;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.TransferenciaContaRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regressão P0-2: o reparo pós-bug ({@code tentarRepararContaPosBug}) deve usar a fórmula
 * completa do ledger (abertura + movimentos). Na lógica antiga a abertura era descartada
 * (ledger "abertura zero") e uma conta com saldo inicial de R$ 2.000 perdia esse valor.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReparoPosBugPreservaAberturaRegressionTest {

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

    @InjectMocks private SaldoService saldoService;

    @Test
    void reparoPosBug_preservaSaldoInicialDe2000() {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        ContaBancaria conta = new ContaBancaria();
        conta.setId(7L);
        conta.setUsuario(usuario);
        conta.setSaldoInicial(new BigDecimal("2000.00"));
        // Corrompido por bug antigo: saldo além do piso do cheque especial
        conta.setSaldoAtual(new BigDecimal("-3000.00"));
        conta.setLimiteChequeEspecial(new BigDecimal("1000.00"));

        when(contaBancariaRepository.findByUsuarioIdAndAtivaTrueOrderByPadraoDescNomeAsc(1L))
            .thenReturn(List.of(conta));
        // Sem transações nem transferências: ledger = abertura
        when(transacaoRepository.findEfetivadasPorConta(7L)).thenReturn(List.of());
        when(transferenciaContaRepository.sumValorEntradaPorConta(7L)).thenReturn(null);
        when(transferenciaContaRepository.sumValorSaidaPorConta(7L)).thenReturn(null);

        int reparadas = saldoService.repararSaldosPosBugReconciliacao(1L);

        assertEquals(1, reparadas);
        // Abertura de R$ 2.000 mantida (lógica antiga zerava para 0.00)
        assertEquals(0, conta.getSaldoInicial().compareTo(new BigDecimal("2000.00")));
        ArgumentCaptor<BigDecimal> saldoCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(saldoMovimentacaoService).definirSaldoReconciliado(eq(7L), saldoCaptor.capture());
        assertEquals(0, saldoCaptor.getValue().compareTo(new BigDecimal("2000.00")));
    }
}
