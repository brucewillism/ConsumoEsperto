package com.consumoesperto.regressao;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A projeção do mês parte do saldo em conta, não do património líquido.
 * Evita misturar estoque (dívida total) com fluxo e contar a parcela duas vezes.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjecaoMesCaixaEmprestimoRegressionTest {

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
    @Mock private ComposicaoProjecaoMesService composicaoProjecaoMesService;

    @InjectMocks private SaldoService saldoService;

    @BeforeEach
    void baseProjecao() {
        when(transacaoRepository.sumConfirmadaByUsuarioIdAndTipoAndPeriodo(any(), any(), any(), any()))
            .thenReturn(BigDecimal.ZERO);
        when(rendaConfigService.getRendaMensalEstimada(any())).thenReturn(new BigDecimal("5000.00"));
        RendaConfigDTO cfg = new RendaConfigDTO();
        cfg.setTipoConfiguracaoRenda(TipoConfiguracaoRenda.CONTRACHEQUE);
        cfg.setDiaPagamento(5);
        when(rendaConfigService.obterDto(any())).thenReturn(Optional.of(cfg));
        when(transacaoRepository.sumReceitaSalarialConfirmadaPeriodo(any(), any(), any()))
            .thenReturn(new BigDecimal("5000.00"));
        when(planejamentoFiscalService.somarReceitasPrevistasNoMes(any(), any())).thenReturn(BigDecimal.ZERO);
        when(conciliacaoAuditoriaService.receitasFiscaisLiquidasNoMes(any(), any(), any()))
            .thenReturn(BigDecimal.ZERO);
        when(transacaoRepository.sumReceitaDecimoTerceiroPrevistaPeriodo(any(), any(), any()))
            .thenReturn(BigDecimal.ZERO);
        when(transacaoRepository.sumReceitaDecimoTerceiroConfirmadaPeriodo(any(), any(), any()))
            .thenReturn(BigDecimal.ZERO);
        when(transacaoRepository.sumValorConfirmadaByUsuarioIdAndTipoTransacao(any(), any()))
            .thenReturn(BigDecimal.ZERO);
        when(transacaoRepository.sumDespesaConfirmadaCaixaPorUsuarioId(any())).thenReturn(BigDecimal.ZERO);
    }

    @Test
    void emprestimoAtivoComCaixaSaudavel_projecaoDoMesPositiva() {
        when(contaBancariaService.possuiContasAtivas(1L)).thenReturn(true);
        when(contaBancariaService.somarSaldosAtivos(1L)).thenReturn(new BigDecimal("10000.00"));
        when(transacaoRepository.sumPassivoEmprestimoAtivo(1L)).thenReturn(new BigDecimal("50000.00"));
        when(composicaoProjecaoMesService.comporDespesasPrevistasMes(
            eq(1L), any(YearMonth.class), any(LocalDate.class), any(BigDecimal.class)))
            .thenReturn(new BigDecimal("800.00"));

        SaldoService.ProjecaoMesCaixa p = saldoService.calcularProjecaoMes(1L);

        assertEquals(0, new BigDecimal("-40000.00").compareTo(saldoService.patrimonioLiquido(1L)),
            "património líquido continua a descontar o passivo total");
        assertEquals(0, new BigDecimal("10000.00").compareTo(p.saldoEmConta()));
        assertTrue(p.saldoProjetadoFimMes().signum() > 0,
            "caixa saudável + parcela do mês não pode fechar negativo só por causa da dívida de longo prazo");
        assertEquals(0, new BigDecimal("9200.00").compareTo(p.saldoProjetadoFimMes()),
            "10000 − 800 (parcela); salário já confirmado não entra de novo");
    }

    @Test
    void parcelaQueDebitaContaEntraUmaUnicaVez() {
        when(contaBancariaService.possuiContasAtivas(1L)).thenReturn(true);
        when(contaBancariaService.somarSaldosAtivos(1L)).thenReturn(new BigDecimal("10000.00"));
        when(transacaoRepository.sumPassivoEmprestimoAtivo(1L)).thenReturn(new BigDecimal("50000.00"));
        when(composicaoProjecaoMesService.comporDespesasPrevistasMes(
            eq(1L), any(YearMonth.class), any(LocalDate.class), any(BigDecimal.class)))
            .thenReturn(new BigDecimal("800.00"));

        SaldoService.ProjecaoMesCaixa p = saldoService.calcularProjecaoMes(1L);

        assertEquals(0, new BigDecimal("800.00").compareTo(p.despesasPrevistas()),
            "só a parcela do mês, não o saldo devedor de 50k");
        BigDecimal seContasseDuasVezes = new BigDecimal("10000.00")
            .subtract(new BigDecimal("50000.00"))
            .subtract(new BigDecimal("800.00"));
        assertTrue(p.saldoProjetadoFimMes().compareTo(seContasseDuasVezes) > 0);
    }

    @Test
    void consignadoEmFolhaNaoEntraNasSaidasDeCaixa() {
        when(contaBancariaService.possuiContasAtivas(4L)).thenReturn(true);
        when(contaBancariaService.somarSaldosAtivos(4L)).thenReturn(new BigDecimal("8000.00"));
        when(transacaoRepository.sumPassivoEmprestimoAtivo(4L)).thenReturn(new BigDecimal("24000.00"));
        when(composicaoProjecaoMesService.comporDespesasPrevistasMes(
            eq(4L), any(YearMonth.class), any(LocalDate.class), any(BigDecimal.class)))
            .thenReturn(BigDecimal.ZERO.setScale(2));

        SaldoService.ProjecaoMesCaixa p = saldoService.calcularProjecaoMes(4L);

        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(p.despesasPrevistas()));
        assertEquals(0, new BigDecimal("8000.00").compareTo(p.saldoProjetadoFimMes()));
        assertEquals(0, new BigDecimal("-16000.00").compareTo(saldoService.patrimonioLiquido(4L)),
            "Visão Geral / património líquido segue descontando o consignado em folha");
    }

    @Test
    void consignadoFolha_caixa10000_projecaoIgualLiquidez_passivoNaGeral() {
        when(contaBancariaService.possuiContasAtivas(1L)).thenReturn(true);
        when(contaBancariaService.somarSaldosAtivos(1L)).thenReturn(new BigDecimal("10000.00"));
        when(transacaoRepository.sumPassivoEmprestimoAtivo(1L)).thenReturn(new BigDecimal("50000.00"));
        when(composicaoProjecaoMesService.comporDespesasPrevistasMes(
            eq(1L), any(YearMonth.class), any(LocalDate.class), any(BigDecimal.class)))
            .thenReturn(BigDecimal.ZERO.setScale(2));

        SaldoService.ProjecaoMesCaixa p = saldoService.calcularProjecaoMes(1L);

        assertEquals(0, new BigDecimal("10000.00").compareTo(p.saldoProjetadoFimMes()));
        assertEquals(0, new BigDecimal("-40000.00").compareTo(saldoService.patrimonioLiquido(1L)));
    }

    @Test
    void cartaoParcelado_fatura2000_projecao8000() {
        when(contaBancariaService.possuiContasAtivas(1L)).thenReturn(true);
        when(contaBancariaService.somarSaldosAtivos(1L)).thenReturn(new BigDecimal("10000.00"));
        when(transacaoRepository.sumPassivoEmprestimoAtivo(1L)).thenReturn(BigDecimal.ZERO);
        when(composicaoProjecaoMesService.comporDespesasPrevistasMes(
            eq(1L), any(YearMonth.class), any(LocalDate.class), any(BigDecimal.class)))
            .thenReturn(new BigDecimal("2000.00"));

        SaldoService.ProjecaoMesCaixa p = saldoService.calcularProjecaoMes(1L);

        assertEquals(0, new BigDecimal("8000.00").compareTo(p.saldoProjetadoFimMes()));
        assertTrue(p.saldoProjetadoFimMes().compareTo(new BigDecimal("7500.00")) != 0,
            "não pode ser 7500 (fatura + parcela do notebook)");
    }

    @Test
    void emprestimoMaisCartao_7200() {
        when(contaBancariaService.possuiContasAtivas(1L)).thenReturn(true);
        when(contaBancariaService.somarSaldosAtivos(1L)).thenReturn(new BigDecimal("10000.00"));
        when(composicaoProjecaoMesService.comporDespesasPrevistasMes(
            eq(1L), any(YearMonth.class), any(LocalDate.class), any(BigDecimal.class)))
            .thenReturn(new BigDecimal("2800.00"));

        assertEquals(0, new BigDecimal("7200.00").compareTo(
            saldoService.calcularProjecaoMes(1L).saldoProjetadoFimMes()));
    }

    @Test
    void receitaFutura_naoEstaNoSaldo_entraNoGap() {
        when(contaBancariaService.possuiContasAtivas(1L)).thenReturn(true);
        when(contaBancariaService.somarSaldosAtivos(1L)).thenReturn(new BigDecimal("3000.00"));
        when(transacaoRepository.sumReceitaSalarialConfirmadaPeriodo(any(), any(), any()))
            .thenReturn(BigDecimal.ZERO);
        when(composicaoProjecaoMesService.comporDespesasPrevistasMes(
            eq(1L), any(YearMonth.class), any(LocalDate.class), any(BigDecimal.class)))
            .thenReturn(new BigDecimal("2000.00"));

        assertEquals(0, new BigDecimal("6000.00").compareTo(
            saldoService.calcularProjecaoMes(1L).saldoProjetadoFimMes()),
            "3000 + 5000 salário restante − 2000");
    }

    @Test
    void receitaJaRecebida_naoSomaDeNovo() {
        when(contaBancariaService.possuiContasAtivas(1L)).thenReturn(true);
        when(contaBancariaService.somarSaldosAtivos(1L)).thenReturn(new BigDecimal("8000.00"));
        when(transacaoRepository.sumReceitaSalarialConfirmadaPeriodo(any(), any(), any()))
            .thenReturn(new BigDecimal("5000.00"));
        when(composicaoProjecaoMesService.comporDespesasPrevistasMes(
            eq(1L), any(YearMonth.class), any(LocalDate.class), any(BigDecimal.class)))
            .thenReturn(new BigDecimal("2000.00"));

        SaldoService.ProjecaoMesCaixa p = saldoService.calcularProjecaoMes(1L);
        assertEquals(0, new BigDecimal("6000.00").compareTo(p.saldoProjetadoFimMes()),
            "8000 − 2000; não 8000+5000−2000=11000");
        assertEquals(-1, p.saldoProjetadoFimMes().compareTo(new BigDecimal("11000.00")));
    }

    @Test
    void despesaJaPaga_composicaoZero_naoDescontaDeNovo() {
        when(contaBancariaService.possuiContasAtivas(1L)).thenReturn(true);
        when(contaBancariaService.somarSaldosAtivos(1L)).thenReturn(new BigDecimal("8000.00"));
        when(composicaoProjecaoMesService.comporDespesasPrevistasMes(
            eq(1L), any(YearMonth.class), any(LocalDate.class), any(BigDecimal.class)))
            .thenReturn(BigDecimal.ZERO.setScale(2));

        assertEquals(0, new BigDecimal("8000.00").compareTo(
            saldoService.calcularProjecaoMes(1L).saldoProjetadoFimMes()));
    }

    @Test
    void mediaAntiSustoUsaSoGastoVariavel() {
        when(contaBancariaService.possuiContasAtivas(1L)).thenReturn(true);
        when(contaBancariaService.somarSaldosAtivos(1L)).thenReturn(new BigDecimal("10000.00"));
        when(transacaoRepository.sumConfirmadaByUsuarioIdAndTipoAndPeriodo(any(), any(), any(), any()))
            .thenReturn(new BigDecimal("5000.00"));
        when(transacaoRepository.sumDespesaVariavelConfirmadaPeriodo(any(), any(), any()))
            .thenReturn(BigDecimal.ZERO);
        when(composicaoProjecaoMesService.comporDespesasPrevistasMes(
            eq(1L), any(YearMonth.class), any(LocalDate.class), any(BigDecimal.class)))
            .thenReturn(new BigDecimal("2000.00"));

        org.mockito.ArgumentCaptor<BigDecimal> media = org.mockito.ArgumentCaptor.forClass(BigDecimal.class);
        saldoService.calcularProjecaoMes(1L);
        verify(composicaoProjecaoMesService).comporDespesasPrevistasMes(
            eq(1L), any(YearMonth.class), any(LocalDate.class), media.capture());
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(media.getValue()),
            "gasto de fatura/parcela não vira variável Anti-Susto");
    }
}
