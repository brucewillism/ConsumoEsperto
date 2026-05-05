package com.consumoesperto.service;

import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.CategoriaRepository;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.TransacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para TransacaoService
 */
@ExtendWith(MockitoExtension.class)
class TransacaoServiceTest {

    @Mock
    private TransacaoRepository transacaoRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private SaldoService saldoService;

    @Mock
    private FaturaRepository faturaRepository;

    @Mock
    private CartaoCreditoRepository cartaoCreditoRepository;

    @Mock
    private FaturaService faturaService;

    @InjectMocks
    private TransacaoService transacaoService;

    private Usuario usuario;
    private Transacao transacao;
    private TransacaoDTO transacaoDTO;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setId(1L);
        usuario.setEmail("teste@teste.com");
        usuario.setNome("Usuário Teste");

        transacao = new Transacao();
        transacao.setId(1L);
        transacao.setUsuario(usuario);
        transacao.setDescricao("Compra teste");
        transacao.setValor(new BigDecimal("100.00"));
        transacao.setDataTransacao(LocalDateTime.now());
        transacao.setTipoTransacao(Transacao.TipoTransacao.DESPESA);
        transacao.setExcluido(false);

        transacaoDTO = new TransacaoDTO();
        transacaoDTO.setId(1L);
        transacaoDTO.setDescricao("Compra teste");
        transacaoDTO.setValor(new BigDecimal("100.00"));
        transacaoDTO.setDataTransacao(LocalDateTime.now());
        transacaoDTO.setTipoTransacao(TransacaoDTO.TipoTransacao.DESPESA);
    }

    @Test
    void testCriarTransacao_Sucesso() {
        when(transacaoRepository.save(any(Transacao.class))).thenReturn(transacao);

        TransacaoDTO result = transacaoService.criarTransacao(transacaoDTO, 1L);

        assertNotNull(result);
        assertEquals("Compra teste", result.getDescricao());
        verify(transacaoRepository, times(1)).save(any(Transacao.class));
        verify(saldoService, times(1)).notificarAlteracaoSaldo(1L);
    }

    @Test
    void testBuscarPorId_Encontrado() {
        when(transacaoRepository.findById(1L)).thenReturn(Optional.of(transacao));

        TransacaoDTO result = transacaoService.buscarPorId(1L, 1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Compra teste", result.getDescricao());
    }

    @Test
    void testBuscarPorId_NaoEncontrado() {
        when(transacaoRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> {
            transacaoService.buscarPorId(1L, 1L);
        });
    }

    @Test
    void testExcluir_Sucesso() {
        when(transacaoRepository.findById(1L)).thenReturn(Optional.of(transacao));
        when(transacaoRepository.save(any(Transacao.class))).thenAnswer(inv -> inv.getArgument(0));

        transacaoService.deletarTransacao(1L, 1L);

        ArgumentCaptor<Transacao> captor = ArgumentCaptor.forClass(Transacao.class);
        verify(transacaoRepository, times(1)).save(captor.capture());
        assertTrue(captor.getValue().isExcluido());
        verify(saldoService, times(1)).notificarAlteracaoSaldo(1L);
    }
}
