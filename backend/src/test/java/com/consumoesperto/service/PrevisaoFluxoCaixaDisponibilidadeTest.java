package com.consumoesperto.service;

import com.consumoesperto.dto.DisponibilidadeRealDTO;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrevisaoFluxoCaixaDisponibilidadeTest {

    @Mock private SaldoService saldoService;
    @Mock private FaturaRepository faturaRepository;
    @Mock private TransacaoRepository transacaoRepository;
    @Mock private RecurringExpenseDetectionService recurringExpenseDetectionService;
    @Mock private JarvisProtocolService jarvisProtocolService;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private DespesaFixaService despesaFixaService;
    @Mock private MarketDataService marketDataService;
    @Mock private ProvisaoMemoriaSentinelaService provisaoMemoriaSentinelaService;
    @Mock private PlanejamentoFiscalService planejamentoFiscalService;

    @InjectMocks private PrevisaoFluxoCaixaService service;

    @BeforeEach
    void setup() {
        when(recurringExpenseDetectionService.detectar(1L)).thenReturn(List.of());
        when(despesaFixaService.somarValorRestanteNoMes(any(), any())).thenReturn(BigDecimal.ZERO.setScale(2));
        when(faturaRepository.sumValorFaturasPendentesByUsuarioId(1L)).thenReturn(BigDecimal.ZERO.setScale(2));
        when(transacaoRepository.findByUsuarioIdAndRecorrenteIsTrueAndTipoTransacao(any(), any()))
            .thenReturn(List.of());
        when(jarvisProtocolService.resolveVocative(any(), any())).thenReturn("Bruce");
        when(jarvisProtocolService.proativoDisponibilidadeReal(
            any(), any(), anyInt(), any(), any(), any(), any())).thenReturn("ok");
        when(saldoService.saldoContaCorrente(1L)).thenReturn(new BigDecimal("10000.00"));
    }

    @Test
    void naoUsaPatrimonioLiquidoNemPassivoDe50k() {
        when(transacaoRepository.sumParcelasEmprestimoPrevistasNoMes(any(), any(), any()))
            .thenReturn(new BigDecimal("800.00"));

        DisponibilidadeRealDTO d = service.calcularDisponibilidadeReal(1L);

        assertEquals(0, new BigDecimal("9200.00").compareTo(d.getDisponivelAposObrigacoes()));
        verify(saldoService, never()).patrimonioLiquido(any());
        verify(transacaoRepository, never()).sumPassivoEmprestimoAtivo(any());
    }

    @Test
    void consignadoFolhaNaoEntraNaDisponibilidade() {
        when(transacaoRepository.sumParcelasEmprestimoPrevistasNoMes(any(), any(), any()))
            .thenReturn(BigDecimal.ZERO.setScale(2));

        DisponibilidadeRealDTO d = service.calcularDisponibilidadeReal(1L);

        assertEquals(0, new BigDecimal("10000.00").compareTo(d.getDisponivelAposObrigacoes()));
    }
}
