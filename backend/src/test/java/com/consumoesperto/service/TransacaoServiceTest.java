package com.consumoesperto.service;

import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.repository.CategoriaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private UsuarioRepository usuarioRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

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

        transacaoDTO = new TransacaoDTO();
        transacaoDTO.setId(1L);
        transacaoDTO.setDescricao("Compra teste");
        transacaoDTO.setValor(new BigDecimal("100.00"));
        transacaoDTO.setDataTransacao(LocalDateTime.now());
        transacaoDTO.setTipoTransacao(TransacaoDTO.TipoTransacao.DESPESA);
    }

    @Test
    void testCriarTransacao_Sucesso() {
        // Arrange
        // O serviço cria um novo Usuario diretamente, não usa o repositório
        when(transacaoRepository.save(any(Transacao.class))).thenReturn(transacao);

        // Act
        TransacaoDTO result = transacaoService.criarTransacao(transacaoDTO, 1L);

        // Assert
        assertNotNull(result);
        assertEquals("Compra teste", result.getDescricao());
        verify(transacaoRepository, times(1)).save(any(Transacao.class));
    }

    @Test
    void testBuscarPorId_Encontrado() {
        // Arrange
        when(transacaoRepository.findById(1L)).thenReturn(Optional.of(transacao));

        // Act
        TransacaoDTO result = transacaoService.buscarPorId(1L, 1L);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Compra teste", result.getDescricao());
    }

    @Test
    void testBuscarPorId_NaoEncontrado() {
        // Arrange
        when(transacaoRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            transacaoService.buscarPorId(1L, 1L);
        });
    }

    @Test
    void testExcluir_Sucesso() {
        // Arrange
        when(transacaoRepository.findById(1L)).thenReturn(Optional.of(transacao));
        doNothing().when(transacaoRepository).delete(transacao);

        // Act
        transacaoService.deletarTransacao(1L, 1L);

        // Assert
        verify(transacaoRepository, times(1)).delete(transacao);
    }
}

