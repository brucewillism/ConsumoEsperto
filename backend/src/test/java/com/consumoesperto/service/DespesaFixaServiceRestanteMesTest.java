package com.consumoesperto.service;

import com.consumoesperto.model.DespesaFixa;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.ContaBancariaRepository;
import com.consumoesperto.repository.DespesaFixaRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DespesaFixaServiceRestanteMesTest {

    @Mock private DespesaFixaRepository despesaFixaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private ContaBancariaRepository contaBancariaRepository;
    @Mock private WhatsAppNotificationService whatsAppNotificationService;
    @Mock private JarvisProtocolService jarvisProtocolService;
    @Mock private TextMatcherService textMatcherService;
    @Mock private TransacaoRepository transacaoRepository;

    private DespesaFixaService service;

    @BeforeEach
    void setup() {
        service = new DespesaFixaService(
            despesaFixaRepository, usuarioRepository, contaBancariaRepository,
            whatsAppNotificationService, jarvisProtocolService, textMatcherService,
            transacaoRepository);
    }

    @Test
    void internetNaoLancada_entraUmaVez() {
        LocalDate ref = LocalDate.of(2026, 9, 18);
        when(despesaFixaRepository.findByUsuarioIdOrderByDiaVencimentoAscIdAsc(1L))
            .thenReturn(List.of(fixa("Internet", "120.00", 10)));
        when(transacaoRepository.findDescricoesDespesaConfirmadaNoPeriodo(eq(1L), any(), any()))
            .thenReturn(List.of());

        assertEquals(0, new BigDecimal("120.00").compareTo(service.somarValorRestanteNoMes(1L, ref)));
    }

    @Test
    void internetJaDebitadaComPrefixo_naoDescontaDeNovo() {
        LocalDate ref = LocalDate.of(2026, 9, 18);
        when(despesaFixaRepository.findByUsuarioIdOrderByDiaVencimentoAscIdAsc(1L))
            .thenReturn(List.of(fixa("Internet", "120.00", 20)));
        when(transacaoRepository.findDescricoesDespesaConfirmadaNoPeriodo(eq(1L), any(), any()))
            .thenReturn(List.of("Despesa fixa: Internet"));

        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(service.somarValorRestanteNoMes(1L, ref)));
    }

    @Test
    void internetJaLancadaSemPrefixo_naoDescontaDeNovo() {
        LocalDate ref = LocalDate.of(2026, 9, 10);
        when(despesaFixaRepository.findByUsuarioIdOrderByDiaVencimentoAscIdAsc(1L))
            .thenReturn(List.of(fixa("Internet", "1000.00", 25)));
        when(transacaoRepository.findDescricoesDespesaConfirmadaNoPeriodo(eq(1L), any(), any()))
            .thenReturn(List.of("Internet"));

        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(service.somarValorRestanteNoMes(1L, ref)),
            "despesa fixa REALIZADA não volta a sair");
    }

    @Test
    void vencimentoHojeNaoPago_aindaEntra() {
        LocalDate ref = LocalDate.of(2026, 9, 10);
        when(despesaFixaRepository.findByUsuarioIdOrderByDiaVencimentoAscIdAsc(1L))
            .thenReturn(List.of(fixa("Luz", "200.00", 10)));
        when(transacaoRepository.findDescricoesDespesaConfirmadaNoPeriodo(eq(1L), any(), any()))
            .thenReturn(List.of());

        assertEquals(0, new BigDecimal("200.00").compareTo(service.somarValorRestanteNoMes(1L, ref)));
    }

    @Test
    void inicioDoMes_todasEmAberto() {
        LocalDate ref = LocalDate.of(2026, 9, 1);
        when(despesaFixaRepository.findByUsuarioIdOrderByDiaVencimentoAscIdAsc(1L))
            .thenReturn(List.of(fixa("A", "10.00", 5), fixa("B", "20.00", 28)));
        when(transacaoRepository.findDescricoesDespesaConfirmadaNoPeriodo(eq(1L), any(), any()))
            .thenReturn(List.of());

        assertEquals(0, new BigDecimal("30.00").compareTo(service.somarValorRestanteNoMes(1L, ref)));
    }

    @Test
    void ultimoDiaDezembroEFevereiro_fixaEmAbertoEntra() {
        when(despesaFixaRepository.findByUsuarioIdOrderByDiaVencimentoAscIdAsc(1L))
            .thenReturn(List.of(fixa("Aluguel", "1500.00", 31)));
        when(transacaoRepository.findDescricoesDespesaConfirmadaNoPeriodo(eq(1L), any(), any()))
            .thenReturn(List.of());

        assertEquals(0, new BigDecimal("1500.00").compareTo(
            service.somarValorRestanteNoMes(1L, LocalDate.of(2026, 12, 31))));
        assertEquals(0, new BigDecimal("1500.00").compareTo(
            service.somarValorRestanteNoMes(1L, LocalDate.of(2026, 2, 28))));
    }

    @Test
    void assinaturaNaoDuplicaFixaNetflix() {
        LocalDate ref = LocalDate.of(2026, 9, 5);
        when(despesaFixaRepository.findByUsuarioIdOrderByDiaVencimentoAscIdAsc(1L))
            .thenReturn(List.of(fixa("Netflix", "55.90", 8)));
        when(transacaoRepository.findDescricoesDespesaConfirmadaNoPeriodo(eq(1L), any(), any()))
            .thenReturn(List.of("Netflix"));

        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(service.somarValorRestanteNoMes(1L, ref)),
            "lançamento do mês da assinatura cancela a fixa equivalente");
    }

    private static DespesaFixa fixa(String descricao, String valor, int dia) {
        Usuario u = new Usuario();
        u.setId(1L);
        DespesaFixa d = new DespesaFixa();
        d.setUsuario(u);
        d.setDescricao(descricao);
        d.setValor(new BigDecimal(valor));
        d.setDiaVencimento(dia);
        return d;
    }
}
