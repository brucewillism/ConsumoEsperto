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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** CF-02: PAGA sem caixa exige origem EXTERNA. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FaturaQuitadaExternaRegressionTest {

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

        fatura = new Fatura();
        fatura.setId(99L);
        fatura.setCartaoCredito(cartao);
        fatura.setValorFatura(new BigDecimal("1000.00"));
        fatura.setStatusFatura(Fatura.StatusFatura.ABERTA);

        when(faturaRepository.findByIdAndCartaoCreditoUsuarioId(99L, 1L)).thenReturn(Optional.of(fatura));
        when(faturaRepository.save(any(Fatura.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transacaoRepository.sumPagamentoFaturaConfirmadoPorFaturaId(99L)).thenReturn(BigDecimal.ZERO);
    }

    @Test
    void marcarPagaSemPagamento_semExterna_lancaExcecao() {
        FaturaDTO dto = new FaturaDTO();
        dto.setStatusFatura(Fatura.StatusFatura.PAGA);
        assertThrows(IllegalArgumentException.class, () -> faturaService.atualizarFatura(99L, dto, 1L));
    }

    @Test
    void marcarPagaExterna_semCaixa_aceita() {
        FaturaDTO dto = new FaturaDTO();
        dto.setStatusFatura(Fatura.StatusFatura.PAGA);
        dto.setOrigemQuitacao(Fatura.OrigemQuitacao.EXTERNA);

        FaturaDTO out = faturaService.atualizarFatura(99L, dto, 1L);

        assertEquals(Fatura.StatusFatura.PAGA, out.getStatusFatura());
        assertEquals(Fatura.OrigemQuitacao.EXTERNA, fatura.getOrigemQuitacao());
    }
}
