package com.consumoesperto.regressao;

import com.consumoesperto.config.ForecastProjecaoConfig;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.Usuario;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Regressão EM-01: tomar empréstimo não infla patrimônio líquido — crédito em conta menos parcelas PREVISTO vincendas.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmprestimoPatrimonioRegressionTest {

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
    void patrimonioLiquido_descontaPassivoEmprestimoVincendas() {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        ContaBancaria conta = new ContaBancaria();
        conta.setId(10L);
        conta.setUsuario(usuario);
        conta.setAtiva(true);
        conta.setSaldoAtual(new BigDecimal("10000.00"));

        when(contaBancariaService.possuiContasAtivas(1L)).thenReturn(true);
        when(contaBancariaService.somarSaldosAtivos(1L)).thenReturn(new BigDecimal("10000.00"));
        when(transacaoRepository.sumPassivoEmprestimoAtivo(1L))
            .thenReturn(new BigDecimal("12000.00"));

        BigDecimal patrimonio = saldoService.patrimonioLiquido(1L);

        assertEquals(0, patrimonio.compareTo(new BigDecimal("-2000.00")),
            "crédito do empréstimo (10k) menos parcelas vincendas (12k) = -2k, não +10k");
    }

    @Test
    void patrimonioLiquido_semEmprestimo_igualAtivos() {
        when(contaBancariaService.possuiContasAtivas(2L)).thenReturn(false);
        when(transacaoRepository.sumValorConfirmadaByUsuarioIdAndTipoTransacao(any(), any()))
            .thenReturn(BigDecimal.ZERO);
        when(transacaoRepository.sumDespesaConfirmadaCaixaPorUsuarioId(2L)).thenReturn(BigDecimal.ZERO);
        when(transacaoRepository.sumPassivoEmprestimoAtivo(2L)).thenReturn(BigDecimal.ZERO);

        assertEquals(0, saldoService.patrimonioLiquido(2L).compareTo(BigDecimal.ZERO.setScale(2)));
    }

    @Test
    void patrimonioLiquido_incluiParcelasVencidasNoPassivo() {
        when(contaBancariaService.possuiContasAtivas(3L)).thenReturn(true);
        when(contaBancariaService.somarSaldosAtivos(3L)).thenReturn(new BigDecimal("5000.00"));
        when(transacaoRepository.sumPassivoEmprestimoAtivo(3L))
            .thenReturn(new BigDecimal("800.00"));

        BigDecimal patrimonio = saldoService.patrimonioLiquido(3L);
        assertEquals(0, patrimonio.compareTo(new BigDecimal("4200.00")),
            "passivo inclui parcelas vencidas PREVISTO, não só vincendas");
    }

    @Test
    void patrimonioLiquido_consignadoFolha_naoInflaPatrimonio() {
        // Crédito 10k na conta; 12k de parcelas PREVISTO (descontoEmFolha=true no registo real).
        when(contaBancariaService.possuiContasAtivas(4L)).thenReturn(true);
        when(contaBancariaService.somarSaldosAtivos(4L)).thenReturn(new BigDecimal("10000.00"));
        when(transacaoRepository.sumPassivoEmprestimoAtivo(4L))
            .thenReturn(new BigDecimal("12000.00"));

        BigDecimal patrimonio = saldoService.patrimonioLiquido(4L);

        assertEquals(0, patrimonio.compareTo(new BigDecimal("-2000.00")),
            "consignado em folha: crédito na conta menos passivo total — não fica +10k");
    }
}
