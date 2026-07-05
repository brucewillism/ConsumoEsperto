package com.consumoesperto.regressao;

import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.CategoriaRepository;
import com.consumoesperto.repository.ContaBancariaRepository;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.MovimentacaoSaldoLogRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.service.ContaBancariaService;
import com.consumoesperto.service.FaturaService;
import com.consumoesperto.service.FinancialProactiveService;
import com.consumoesperto.service.SaldoMovimentacaoService;
import com.consumoesperto.service.SaldoService;
import com.consumoesperto.service.ScoreService;
import com.consumoesperto.service.TransacaoSemanticaIndexService;
import com.consumoesperto.service.TransacaoService;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regressão P0-1: exclusão em lote de parcelamento (FUTURAS/TUDO) deve estornar o saldo
 * de cada parcela (contrato de {@code deletarTransacao}) e ser idempotente.
 * Na lógica antiga o lote soft-deletava sem chamar {@code aplicarExclusao} — saldo ficava inflado.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExclusaoLoteEstornoRegressionTest {

    @Mock private TransacaoRepository transacaoRepository;
    @Mock private CategoriaRepository categoriaRepository;
    @Mock private SaldoService saldoService;
    @Mock private FaturaRepository faturaRepository;
    @Mock private CartaoCreditoRepository cartaoCreditoRepository;
    @Mock private FaturaService faturaService;
    @Mock private FinancialProactiveService financialProactiveService;
    @Mock private ScoreService scoreService;
    @Mock private TransacaoSemanticaIndexService transacaoSemanticaIndexService;
    @Mock private ContaBancariaService contaBancariaService;
    @Mock private SaldoMovimentacaoService saldoMovimentacaoService;

    @InjectMocks private TransacaoService transacaoService;

    private Usuario usuario;
    private ContaBancaria conta;
    private Transacao p1;
    private Transacao p2;
    private Transacao p3Prevista;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setId(1L);
        conta = new ContaBancaria();
        conta.setId(7L);
        conta.setUsuario(usuario);
        p1 = parcela(11L, 1, Transacao.StatusConferencia.CONFIRMADA);
        p2 = parcela(12L, 2, Transacao.StatusConferencia.CONFIRMADA);
        p3Prevista = parcela(13L, 3, Transacao.StatusConferencia.PREVISTO);
    }

    private Transacao parcela(Long id, int numero, Transacao.StatusConferencia status) {
        Transacao t = new Transacao();
        t.setId(id);
        t.setUsuario(usuario);
        t.setContaBancaria(conta);
        t.setValor(new BigDecimal("100.00"));
        t.setTipoTransacao(Transacao.TipoTransacao.DESPESA);
        t.setStatusConferencia(status);
        t.setDataTransacao(LocalDateTime.now().minusDays(1));
        t.setGrupoParcelaId("G1");
        t.setParcelaAtual(numero);
        t.setTotalParcelas(3);
        t.setExcluido(false);
        return t;
    }

    @Test
    void exclusaoTudo_estornaCadaParcelaViaPontoUnico() {
        when(transacaoRepository.findById(11L)).thenReturn(Optional.of(p1));
        when(transacaoRepository.findByUsuarioIdAndGrupoParcelaIdOrderByParcelaAtualAsc(1L, "G1"))
            .thenReturn(List.of(p1, p2, p3Prevista));
        when(transacaoRepository.save(any(Transacao.class))).thenAnswer(inv -> inv.getArgument(0));

        transacaoService.deletarTransacaoComModoParcelamento(11L, 1L, "TUDO");

        // Contrato do estorno: aplicarExclusao para TODA parcela do lote (a guarda
        // CONFIRMADA-apenas vive dentro do SaldoMovimentacaoService — testada abaixo).
        verify(saldoMovimentacaoService).aplicarExclusao(p1);
        verify(saldoMovimentacaoService).aplicarExclusao(p2);
        verify(saldoMovimentacaoService).aplicarExclusao(p3Prevista);
        assertTrue(p1.isExcluido());
        assertTrue(p2.isExcluido());
        assertTrue(p3Prevista.isExcluido());
    }

    @Test
    void exclusaoReprocessada_naoEstornaEmDobro() {
        when(transacaoRepository.findById(11L)).thenReturn(Optional.of(p1));
        when(transacaoRepository.findByUsuarioIdAndGrupoParcelaIdOrderByParcelaAtualAsc(1L, "G1"))
            .thenReturn(List.of(p1, p2, p3Prevista))
            // 2ª execução: @Where(excluido=false) da entidade filtra as parcelas soft-deletadas
            .thenReturn(List.of());
        when(transacaoRepository.save(any(Transacao.class))).thenAnswer(inv -> inv.getArgument(0));

        transacaoService.deletarTransacaoComModoParcelamento(11L, 1L, "TUDO");
        transacaoService.deletarTransacaoComModoParcelamento(11L, 1L, "TUDO");

        verify(saldoMovimentacaoService, times(3)).aplicarExclusao(any(Transacao.class));
    }

    @Test
    void parcelaPrevista_naoImpactaSaldo_confirmadaImpacta() {
        // Fonte da verdade real (sem mock): PREVISTO tem delta zero; CONFIRMADA devolve o valor
        SaldoMovimentacaoService real = new SaldoMovimentacaoService(
            mock(ContaBancariaRepository.class), mock(MovimentacaoSaldoLogRepository.class));

        assertEquals(0, real.deltaSaldo(p3Prevista).compareTo(BigDecimal.ZERO));
        assertEquals(0, real.deltaSaldo(p1).compareTo(new BigDecimal("-100.00")));
    }
}
