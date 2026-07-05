package com.consumoesperto.regressao;

import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.Fatura;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.ImportacaoFaturaCartaoRepository;
import com.consumoesperto.repository.SugestaoContencaoJarvisRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.service.FaturaConciliacaoService;
import com.consumoesperto.service.FaturaService;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Regressão P0-5: competência da compra no cartão — compra ATÉ a data de fechamento entra
 * no ciclo atual; DEPOIS do fechamento vai para o ciclo seguinte. Na lógica antiga toda
 * compra caía na fatura "aberta" independentemente do fechamento.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompetenciaCompraCartaoRegressionTest {

    @Mock private FaturaRepository faturaRepository;
    @Mock private CartaoCreditoRepository cartaoCreditoRepository;
    @Mock private TransacaoRepository transacaoRepository;
    @Mock private ImportacaoFaturaCartaoRepository importacaoFaturaCartaoRepository;
    @Mock private SugestaoContencaoJarvisRepository sugestaoContencaoJarvisRepository;
    @Mock private FaturaConciliacaoService faturaConciliacaoService;
    @Mock private SaldoMovimentacaoService saldoMovimentacaoService;
    @Mock private SaldoService saldoService;

    @InjectMocks private FaturaService faturaService;

    private CartaoCredito cartao;
    private Fatura faturaAgosto;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(faturaService, "diasEntreFechamentoEVencimento", 10);

        Usuario u = new Usuario();
        u.setId(1L);
        cartao = new CartaoCredito();
        cartao.setId(3L);
        cartao.setUsuario(u);
        cartao.setNome("Cartão X");
        cartao.setDiaVencimento(10);

        // Ciclo atual: vencimento 10/08, fechamento 31/07 (12:00)
        faturaAgosto = new Fatura();
        faturaAgosto.setId(50L);
        faturaAgosto.setCartaoCredito(cartao);
        faturaAgosto.setUsuario(u);
        faturaAgosto.setStatusFatura(Fatura.StatusFatura.ABERTA);
        faturaAgosto.setDataVencimento(LocalDateTime.of(2026, 8, 10, 12, 0));
        faturaAgosto.setDataFechamento(LocalDateTime.of(2026, 7, 31, 12, 0));
        faturaAgosto.setValorFatura(BigDecimal.ZERO);

        when(faturaRepository.findByCartaoCreditoIdAndStatusInOrderByDataVencimentoAsc(anyLong(), anyList()))
            .thenReturn(List.of(faturaAgosto));
        when(faturaRepository.findByCartaoCreditoIdOrderByDataVencimentoAsc(3L))
            .thenReturn(List.of(faturaAgosto));
        when(faturaRepository.save(any(Fatura.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void compraAntesDoFechamento_entraNoCicloAtual() {
        Fatura alvo = faturaService.resolverFaturaParaCompra(
            1L, cartao, LocalDateTime.of(2026, 7, 30, 10, 0));
        assertEquals(50L, alvo.getId());
    }

    @Test
    void compraNaDataDoFechamento_aindaEntraNoCicloAtual() {
        Fatura alvo = faturaService.resolverFaturaParaCompra(
            1L, cartao, LocalDateTime.of(2026, 7, 31, 12, 0));
        assertEquals(50L, alvo.getId());
    }

    @Test
    void compraDepoisDoFechamento_vaiParaOCicloSeguinte() {
        Fatura alvo = faturaService.resolverFaturaParaCompra(
            1L, cartao, LocalDateTime.of(2026, 8, 2, 10, 0));
        assertNotEquals(Long.valueOf(50L), alvo.getId());
        assertEquals(YearMonth.of(2026, 9), YearMonth.from(alvo.getDataVencimento()));
        assertEquals(Fatura.StatusFatura.PREVISTA, alvo.getStatusFatura());
    }
}
