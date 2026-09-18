package com.consumoesperto.service;

import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SentinelaProtocolServiceTest {

    @Mock private RecurringExpenseDetectionService recurringExpenseDetectionService;
    @Mock private SaldoService saldoService;
    @Mock private SentinelaBufferSazonalService sentinelaBufferSazonalService;
    @Mock private OpenAiService openAiService;
    @Mock private JarvisProtocolService jarvisProtocolService;
    @Mock private UsuarioRepository usuarioRepository;

    private SentinelaProtocolService service;

    @BeforeEach
    void setup() {
        service = new SentinelaProtocolService(
            recurringExpenseDetectionService, saldoService, sentinelaBufferSazonalService,
            openAiService, jarvisProtocolService, usuarioRepository);
        when(recurringExpenseDetectionService.detectar(1L)).thenReturn(List.of());
        when(sentinelaBufferSazonalService.calcularColchao(1L)).thenReturn(
            new SentinelaBufferSazonalService.ColchaoSazonal(BigDecimal.ZERO.setScale(2), -1, null));
    }

    @Test
    void patrimonioNegativoPorEmprestimoNaoDisparaCautelaSeCaixaPositivo() {
        YearMonth ym = YearMonth.of(2026, 9);
        when(saldoService.calcularProjecaoMes(1L)).thenReturn(new SaldoService.ProjecaoMesCaixa(
            ym,
            new BigDecimal("8000.00"),
            new BigDecimal("200.00"),
            new BigDecimal("1000.00"),
            new BigDecimal("5000.00"),
            BigDecimal.ZERO.setScale(2),
            BigDecimal.ZERO.setScale(2),
            new BigDecimal("800.00"),
            new BigDecimal("7200.00"),
            17,
            30
        ));
        when(saldoService.deltaProjecaoNovaDespesa(org.mockito.ArgumentMatchers.any())).thenReturn(BigDecimal.ZERO.setScale(2));

        Transacao t = despesa(1L);
        SentinelaProtocolService.SentinelaMargemDTO dto = service.calcularMargemSentinela(t);

        assertEquals(SentinelaProtocolService.NivelAlertaSentinela.OK, dto.nivelAlerta());
        assertEquals(0, new BigDecimal("7200.00").compareTo(dto.saldoMarginal()));
        assertEquals(0, new BigDecimal("8000.00").compareTo(dto.patrimonioLiquido()));
    }

    @Test
    void projecaoDeCaixaNegativaECritica() {
        YearMonth ym = YearMonth.of(2026, 9);
        when(saldoService.calcularProjecaoMes(1L)).thenReturn(new SaldoService.ProjecaoMesCaixa(
            ym,
            new BigDecimal("500.00"),
            BigDecimal.ZERO.setScale(2),
            BigDecimal.ZERO.setScale(2),
            BigDecimal.ZERO.setScale(2),
            BigDecimal.ZERO.setScale(2),
            BigDecimal.ZERO.setScale(2),
            new BigDecimal("900.00"),
            new BigDecimal("-400.00"),
            10,
            30
        ));
        when(saldoService.deltaProjecaoNovaDespesa(org.mockito.ArgumentMatchers.any())).thenReturn(BigDecimal.ZERO.setScale(2));

        SentinelaProtocolService.SentinelaMargemDTO dto = service.calcularMargemSentinela(despesa(1L));
        assertEquals(SentinelaProtocolService.NivelAlertaSentinela.CRITICO, dto.nivelAlerta());
        assertEquals(0, new BigDecimal("-400.00").compareTo(dto.saldoMarginal()));
    }

    @Test
    void patrimonioLiquidoNegativo40000_projecaoPositiva_semCautelaPorCaixa() {
        YearMonth ym = YearMonth.of(2026, 9);
        when(saldoService.calcularProjecaoMes(1L)).thenReturn(new SaldoService.ProjecaoMesCaixa(
            ym,
            new BigDecimal("10000.00"),
            BigDecimal.ZERO.setScale(2),
            BigDecimal.ZERO.setScale(2),
            new BigDecimal("5000.00"),
            BigDecimal.ZERO.setScale(2),
            BigDecimal.ZERO.setScale(2),
            new BigDecimal("5000.00"),
            new BigDecimal("5000.00"),
            18,
            30
        ));
        when(saldoService.deltaProjecaoNovaDespesa(org.mockito.ArgumentMatchers.any())).thenReturn(BigDecimal.ZERO.setScale(2));

        SentinelaProtocolService.SentinelaMargemDTO dto = service.calcularMargemSentinela(despesa(1L));
        assertEquals(SentinelaProtocolService.NivelAlertaSentinela.OK, dto.nivelAlerta(),
            "PL −40k não dispara cautela se a projeção de caixa é +5k");
    }

    @Test
    void patrimonioPositivo_projecaoNegativa_cautelaAtiva() {
        YearMonth ym = YearMonth.of(2026, 9);
        when(saldoService.calcularProjecaoMes(1L)).thenReturn(new SaldoService.ProjecaoMesCaixa(
            ym,
            new BigDecimal("200000.00"),
            BigDecimal.ZERO.setScale(2),
            BigDecimal.ZERO.setScale(2),
            BigDecimal.ZERO.setScale(2),
            BigDecimal.ZERO.setScale(2),
            BigDecimal.ZERO.setScale(2),
            new BigDecimal("201000.00"),
            new BigDecimal("-1000.00"),
            18,
            30
        ));
        when(saldoService.deltaProjecaoNovaDespesa(org.mockito.ArgumentMatchers.any())).thenReturn(BigDecimal.ZERO.setScale(2));

        SentinelaProtocolService.SentinelaMargemDTO dto = service.calcularMargemSentinela(despesa(1L));
        assertEquals(SentinelaProtocolService.NivelAlertaSentinela.CRITICO, dto.nivelAlerta(),
            "risco de liquidez com património positivo");
    }

    private static Transacao despesa(Long userId) {
        Usuario u = new Usuario();
        u.setId(userId);
        Transacao t = new Transacao();
        t.setUsuario(u);
        t.setTipoTransacao(Transacao.TipoTransacao.DESPESA);
        t.setValor(new BigDecimal("50.00"));
        return t;
    }
}
