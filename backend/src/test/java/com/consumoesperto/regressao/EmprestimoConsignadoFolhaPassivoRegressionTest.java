package com.consumoesperto.regressao;

import com.consumoesperto.config.ForecastProjecaoConfig;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Consignado com desconto em folha não compõe passivo de patrimônio líquido
 * (premissa: salário registrado já é líquido).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmprestimoConsignadoFolhaPassivoRegressionTest {

    @Mock private TransacaoRepository transacaoRepository;
    @Mock private FaturaRepository faturaRepository;
    @Mock private com.consumoesperto.repository.ContaBancariaRepository contaBancariaRepository;
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
    void patrimonioLiquido_consignadoFolha_igualSaldoConta() {
        when(contaBancariaService.possuiContasAtivas(1L)).thenReturn(true);
        when(contaBancariaService.somarSaldosAtivos(1L)).thenReturn(new BigDecimal("8500.00"));
        when(transacaoRepository.sumPassivoEmprestimoAtivo(1L)).thenReturn(BigDecimal.ZERO);

        assertEquals(0, saldoService.patrimonioLiquido(1L).compareTo(new BigDecimal("8500.00")),
            "consignado desconto em folha não entra no passivo — patrimônio = saldo em conta");
    }
}
