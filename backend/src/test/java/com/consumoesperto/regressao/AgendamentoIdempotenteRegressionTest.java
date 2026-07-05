package com.consumoesperto.regressao;

import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.model.AgendamentoPagamento;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.AgendamentoPagamentoRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.service.AgendamentoPagamentoService;
import com.consumoesperto.service.ContaBancariaService;
import com.consumoesperto.service.TransacaoService;
import com.consumoesperto.service.UsuarioSessaoContextoService;
import com.consumoesperto.service.WhatsAppNotificationService;
import com.consumoesperto.util.AppTimeZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regressão P0-3: agendamento idempotente — a checagem de duplicata e a criação da transação
 * usam a MESMA data (vencimento à meia-noite, fuso do app). Na lógica antiga a criação usava
 * {@code AppTimeZone.agora()} e a checagem usava o vencimento — reprocessar debitava 2×.
 * A guarda roda dentro do lock ({@code findByIdForUpdate}) e a chave não bloqueia um segundo
 * pagamento legítimo com outro vencimento.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgendamentoIdempotenteRegressionTest {

    @Mock private AgendamentoPagamentoRepository agendamentoRepository;
    @Mock private TransacaoRepository transacaoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private ContaBancariaService contaBancariaService;
    @Mock private TransacaoService transacaoService;
    @Mock private UsuarioSessaoContextoService sessaoContextoService;
    @Mock private WhatsAppNotificationService whatsAppNotificationService;

    @InjectMocks private AgendamentoPagamentoService service;

    private Usuario usuario;
    private ContaBancaria conta;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setId(1L);
        conta = new ContaBancaria();
        conta.setId(7L);
        conta.setUsuario(usuario);
        conta.setSaldoAtual(new BigDecimal("5000.00"));
        conta.setNome("Corrente");
        when(contaBancariaService.buscarEntidade(7L, 1L)).thenReturn(conta);
        when(agendamentoRepository.save(any(AgendamentoPagamento.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private AgendamentoPagamento agendamento(Long id, LocalDate vencimento) {
        AgendamentoPagamento ag = new AgendamentoPagamento();
        ag.setId(id);
        ag.setUsuario(usuario);
        ag.setContaDebito(conta);
        ag.setBeneficiario("Enel");
        ag.setValor(new BigDecimal("250.00"));
        ag.setDataVencimento(vencimento);
        ag.setStatus(AgendamentoPagamento.StatusAgendamento.AGENDADO);
        return ag;
    }

    @Test
    void executarDuasVezes_geraUmUnicoDebito_comMesmaDataDaChecagem() {
        LocalDate hoje = AppTimeZone.hoje();
        LocalDateTime dataEsperada = hoje.atStartOfDay();
        AgendamentoPagamento ag = agendamento(5L, hoje);

        when(agendamentoRepository.findByStatusAndDataVencimento(
            eq(AgendamentoPagamento.StatusAgendamento.AGENDADO), eq(hoje)))
            .thenReturn(List.of(ag));
        when(agendamentoRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(ag));
        // 1ª execução: sem duplicata; 2ª: a transação criada já existe na chave (usuario, descrição, data, valor)
        when(transacaoRepository.findByUsuarioIdAndDescricaoAndDataTransacaoAndValor(
            eq(1L), eq("Pagamento agendado: Enel"), eq(dataEsperada), eq(new BigDecimal("250.00"))))
            .thenReturn(List.of())
            .thenReturn(List.of(new Transacao()));

        service.executarPagamentosDoDia();
        ag.setStatus(AgendamentoPagamento.StatusAgendamento.AGENDADO); // simula job reprocessado após falha parcial
        service.executarPagamentosDoDia();

        ArgumentCaptor<TransacaoDTO> captor = ArgumentCaptor.forClass(TransacaoDTO.class);
        verify(transacaoService, times(1)).criarTransacao(captor.capture(), eq(1L), anyBoolean());
        // Regressão central: a data da transação criada é EXATAMENTE a data usada na checagem
        assertEquals(dataEsperada, captor.getValue().getDataTransacao());
        assertEquals(AgendamentoPagamento.StatusAgendamento.PAGO, ag.getStatus());
    }

    @Test
    void segundoPagamentoLegitimo_outroVencimento_naoEBloqueado() {
        LocalDate hoje = AppTimeZone.hoje();
        AgendamentoPagamento ag1 = agendamento(5L, hoje);
        AgendamentoPagamento ag2 = agendamento(6L, hoje); // mesmo beneficiário/valor, outro registro no mesmo dia

        when(agendamentoRepository.findByStatusAndDataVencimento(
            eq(AgendamentoPagamento.StatusAgendamento.AGENDADO), eq(hoje)))
            .thenReturn(List.of(ag1, ag2));
        when(agendamentoRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(ag1));
        when(agendamentoRepository.findByIdForUpdate(6L)).thenReturn(Optional.of(ag2));
        // Chave (usuario, descrição, data, valor): 1º cria; 2º do MESMO dia é barrado (dedup),
        // mas um vencimento diferente teria data distinta e não seria bloqueado.
        when(transacaoRepository.findByUsuarioIdAndDescricaoAndDataTransacaoAndValor(
            any(), any(), any(), any()))
            .thenReturn(List.of())
            .thenReturn(List.of(new Transacao()));

        service.executarPagamentosDoDia();

        // Vencimentos distintos: datas distintas na chave → ambos executam
        AgendamentoPagamento ag3 = agendamento(8L, hoje.plusDays(3));
        when(agendamentoRepository.findByStatusAndDataVencimento(
            eq(AgendamentoPagamento.StatusAgendamento.AGENDADO), eq(hoje)))
            .thenReturn(List.of(ag3));
        when(agendamentoRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(ag3));
        when(transacaoRepository.findByUsuarioIdAndDescricaoAndDataTransacaoAndValor(
            eq(1L), eq("Pagamento agendado: Enel"), eq(hoje.plusDays(3).atStartOfDay()), eq(new BigDecimal("250.00"))))
            .thenReturn(List.of());

        service.executarPagamentosDoDia();

        ArgumentCaptor<TransacaoDTO> captor = ArgumentCaptor.forClass(TransacaoDTO.class);
        verify(transacaoService, times(2)).criarTransacao(captor.capture(), eq(1L), anyBoolean());
        List<TransacaoDTO> criadas = captor.getAllValues();
        assertEquals(hoje.atStartOfDay(), criadas.get(0).getDataTransacao());
        assertEquals(hoje.plusDays(3).atStartOfDay(), criadas.get(1).getDataTransacao());
    }
}
