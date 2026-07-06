package com.consumoesperto.regressao;

import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.service.ContaBancariaService;
import com.consumoesperto.service.EmprestimoService;
import com.consumoesperto.service.FinancialAdviceCalculator;
import com.consumoesperto.service.JarvisContextoFinanceiroService;
import com.consumoesperto.service.MarketDataService;
import com.consumoesperto.service.SaldoMovimentacaoService;
import com.consumoesperto.service.TransacaoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** EM-03: cancelamento acumula creditoEstornado de múltiplas receitas. */
@ExtendWith(MockitoExtension.class)
class EmprestimoCancelMultiplasReceitasRegressionTest {

    @Mock private TransacaoService transacaoService;
    @Mock private TransacaoRepository transacaoRepository;
    @Mock private com.consumoesperto.repository.ContaBancariaRepository contaBancariaRepository;
    @Mock private ContaBancariaService contaBancariaService;
    @Mock private SaldoMovimentacaoService saldoMovimentacaoService;
    @Mock private JarvisContextoFinanceiroService jarvisContextoFinanceiroService;
    @Mock private FinancialAdviceCalculator financialAdviceCalculator;
    @Mock private MarketDataService marketDataService;

    @InjectMocks private EmprestimoService emprestimoService;

    @Test
    void cancelar_somaDuasReceitasConfirmadas() {
        Transacao r1 = receita("e1", new BigDecimal("5000.00"));
        Transacao r2 = receita("e1", new BigDecimal("200.00"));
        when(transacaoRepository.findByUsuarioIdAndEmprestimoIdOrderByDataTransacaoAsc(1L, "e1"))
            .thenReturn(List.of(r1, r2));
        when(transacaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var out = emprestimoService.cancelarEmprestimo(1L, "e1");

        assertEquals(0, out.getValorTomado().compareTo(new BigDecimal("5200.00")));
        verify(saldoMovimentacaoService, org.mockito.Mockito.times(2)).aplicarExclusao(any());
    }

    private static Transacao receita(String emprestimoId, BigDecimal valor) {
        Transacao t = new Transacao();
        t.setEmprestimoId(emprestimoId);
        t.setTipoTransacao(Transacao.TipoTransacao.RECEITA);
        t.setStatusConferencia(Transacao.StatusConferencia.CONFIRMADA);
        t.setValor(valor);
        t.setExcluido(false);
        return t;
    }
}
