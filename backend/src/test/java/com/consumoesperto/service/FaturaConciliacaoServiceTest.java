package com.consumoesperto.service;

import com.consumoesperto.dto.PagamentoFaturaRequest;
import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.Fatura;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.CategoriaRepository;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.TransacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FaturaConciliacaoServiceTest {

    @Mock
    private FaturaRepository faturaRepository;
    @Mock
    private ContaBancariaService contaBancariaService;
    @Mock
    private CategoriaRepository categoriaRepository;
    @Mock
    private TransacaoRepository transacaoRepository;
    @Mock
    private SaldoMovimentacaoService saldoMovimentacaoService;
    @Mock
    private SaldoService saldoService;

    @InjectMocks
    private FaturaConciliacaoService service;

    private Fatura fatura;

    @BeforeEach
    void setUp() {
        Usuario u = new Usuario();
        u.setId(1L);
        CartaoCredito cartao = new CartaoCredito();
        cartao.setId(10L);
        cartao.setUsuario(u);
        fatura = new Fatura();
        fatura.setId(99L);
        fatura.setCartaoCredito(cartao);
        fatura.setValorFatura(new BigDecimal("2895.97"));
        fatura.setValorTotal(new BigDecimal("2895.97"));
        fatura.setStatus(Fatura.StatusFatura.ABERTA);
        fatura.setPaga(false);
    }

    @Test
    void reconciliarMarcaPagaQuandoPagamentoRegistradoCobreTotalComTolerancia() {
        when(transacaoRepository.sumDespesaConfirmadaPorFaturaId(99L))
            .thenReturn(new BigDecimal("2896.00"));
        when(transacaoRepository.sumPagamentoFaturaConfirmadoPorFaturaId(99L))
            .thenReturn(new BigDecimal("2895.97"));
        when(faturaRepository.save(any(Fatura.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean alterou = service.reconciliarStatusPagamento(fatura);

        assertTrue(alterou);
        assertEquals(Fatura.StatusFatura.PAGA, fatura.getStatus());
        assertTrue(fatura.getPaga());
        verify(faturaRepository).save(fatura);
    }

    @Test
    void rejeitaPagamentoParcialQuandoRestanteMaiorQueValorEnviado() {
        when(faturaRepository.findByIdAndCartaoCreditoUsuarioId(99L, 1L))
            .thenReturn(Optional.of(fatura));
        when(transacaoRepository.sumPagamentoFaturaConfirmadoPorFaturaId(99L))
            .thenReturn(new BigDecimal("2500.00"));

        PagamentoFaturaRequest req = new PagamentoFaturaRequest();
        req.setFaturaId(99L);
        req.setContaBancariaId(5L);
        req.setValor(new BigDecimal("2500.00"));

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.pagarFatura(1L, req)
        );
        assertTrue(ex.getMessage().contains("integral"));
    }

    @Test
    void resolverValorDevidoUsaMaiorEntreCampoESomaDespesas() {
        when(transacaoRepository.sumDespesaConfirmadaPorFaturaId(99L))
            .thenReturn(new BigDecimal("2900.00"));

        BigDecimal devido = service.resolverValorDevido(fatura);

        assertEquals(new BigDecimal("2900.00"), devido);
    }
}
