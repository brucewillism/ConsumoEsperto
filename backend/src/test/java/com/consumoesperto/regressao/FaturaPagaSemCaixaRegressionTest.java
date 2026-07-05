package com.consumoesperto.regressao;

import com.consumoesperto.dto.FaturaDTO;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Regressão P0-4: marcar fatura como PAGA via API sem transações {@code PAGAMENTO_FATURA}
 * NÃO pode preencher {@code valorPago} — o valor pago deriva só de pagamentos reais.
 * Na lógica antiga o valorPago era copiado do total da fatura, "pagando" sem caixa.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FaturaPagaSemCaixaRegressionTest {

    @Mock private FaturaRepository faturaRepository;
    @Mock private CartaoCreditoRepository cartaoCreditoRepository;
    @Mock private TransacaoRepository transacaoRepository;
    @Mock private ImportacaoFaturaCartaoRepository importacaoFaturaCartaoRepository;
    @Mock private SugestaoContencaoJarvisRepository sugestaoContencaoJarvisRepository;
    @Mock private FaturaConciliacaoService faturaConciliacaoService;
    @Mock private SaldoMovimentacaoService saldoMovimentacaoService;
    @Mock private SaldoService saldoService;

    @InjectMocks private FaturaService faturaService;

    private Fatura fatura;

    @BeforeEach
    void setUp() {
        Usuario u = new Usuario();
        u.setId(1L);
        CartaoCredito cartao = new CartaoCredito();
        cartao.setId(3L);
        cartao.setUsuario(u);
        cartao.setNome("Cartão X");
        cartao.setBanco("Banco Y");

        fatura = new Fatura();
        fatura.setId(99L);
        fatura.setCartaoCredito(cartao);
        fatura.setValorFatura(new BigDecimal("1000.00"));
        fatura.setValorTotal(new BigDecimal("1000.00"));
        fatura.setStatusFatura(Fatura.StatusFatura.ABERTA);
        fatura.setPaga(false);
        fatura.setDataVencimento(LocalDateTime.now().plusDays(5));

        when(faturaRepository.findByIdAndCartaoCreditoUsuarioId(99L, 1L)).thenReturn(Optional.of(fatura));
        when(faturaRepository.save(any(Fatura.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transacaoRepository.findByFaturaIdOrderByDataTransacaoAscIdAsc(99L)).thenReturn(List.of());
    }

    @Test
    void marcarPagaSemPagamentoReal_naoPreencheValorPago() {
        when(transacaoRepository.sumPagamentoFaturaConfirmadoPorFaturaId(99L)).thenReturn(BigDecimal.ZERO);

        FaturaDTO dto = new FaturaDTO();
        dto.setStatusFatura(Fatura.StatusFatura.PAGA);

        FaturaDTO atualizado = faturaService.atualizarFatura(99L, dto, 1L);

        assertTrue(atualizado.getValorPago() == null
                || atualizado.getValorPago().compareTo(BigDecimal.ZERO) == 0,
            "valorPago não pode ser preenchido sem PAGAMENTO_FATURA real (era copiado do total)");
        assertEquals(Fatura.StatusFatura.PAGA, atualizado.getStatusFatura());
    }

    @Test
    void marcarPaga_comPagamentoReal_derivaValorPagoDasTransacoes() {
        when(transacaoRepository.sumPagamentoFaturaConfirmadoPorFaturaId(99L))
            .thenReturn(new BigDecimal("400.00"));

        FaturaDTO dto = new FaturaDTO();
        dto.setStatusFatura(Fatura.StatusFatura.PAGA);

        FaturaDTO atualizado = faturaService.atualizarFatura(99L, dto, 1L);

        assertEquals(0, atualizado.getValorPago().compareTo(new BigDecimal("400.00")));
    }
}
