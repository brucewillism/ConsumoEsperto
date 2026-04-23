package com.consumoesperto.service;

import com.consumoesperto.model.AutorizacaoBancaria;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.AutorizacaoBancariaRepository;
import com.consumoesperto.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para AutorizacaoBancariaService
 */
@ExtendWith(MockitoExtension.class)
class AutorizacaoBancariaServiceTest {

    @Mock
    private AutorizacaoBancariaRepository autorizacaoBancariaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private AutorizacaoBancariaService autorizacaoBancariaService;

    private Usuario usuario;
    private AutorizacaoBancaria autorizacao;
    private Map<String, Object> tokenResponse;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setId(1L);
        usuario.setEmail("teste@teste.com");
        usuario.setNome("Usuário Teste");

        autorizacao = new AutorizacaoBancaria();
        autorizacao.setId(1L);
        autorizacao.setUsuario(usuario);
        autorizacao.setBanco("MERCADO_PAGO");
        autorizacao.setAccessToken("access_token_123");
        autorizacao.setRefreshToken("refresh_token_123");
        autorizacao.setDataExpiracao(LocalDateTime.now().plusHours(1));
        autorizacao.setAtivo(true);
        autorizacao.setDataCriacao(LocalDateTime.now());
        autorizacao.setDataAtualizacao(LocalDateTime.now());

        tokenResponse = new HashMap<>();
        tokenResponse.put("access_token", "new_access_token");
        tokenResponse.put("refresh_token", "new_refresh_token");
        tokenResponse.put("expires_in", 3600);
    }

    @Test
    void testSalvarAutorizacao_NovaAutorizacao() {
        // Arrange
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(autorizacaoBancariaRepository.findByUsuarioIdAndBanco(1L, "MERCADO_PAGO"))
                .thenReturn(Optional.empty());
        when(autorizacaoBancariaRepository.save(any(AutorizacaoBancaria.class))).thenReturn(autorizacao);

        // Act
        AutorizacaoBancaria result = autorizacaoBancariaService.salvarAutorizacao(
                1L, BankApiService.BankType.MERCADO_PAGO, tokenResponse);

        // Assert
        assertNotNull(result);
        assertEquals("MERCADO_PAGO", result.getBanco());
        verify(autorizacaoBancariaRepository, times(1)).save(any(AutorizacaoBancaria.class));
    }

    @Test
    void testSalvarAutorizacao_AtualizarExistente() {
        // Arrange
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(autorizacaoBancariaRepository.findByUsuarioIdAndBanco(1L, "MERCADO_PAGO"))
                .thenReturn(Optional.of(autorizacao));
        when(autorizacaoBancariaRepository.save(any(AutorizacaoBancaria.class))).thenReturn(autorizacao);

        // Act
        AutorizacaoBancaria result = autorizacaoBancariaService.salvarAutorizacao(
                1L, BankApiService.BankType.MERCADO_PAGO, tokenResponse);

        // Assert
        assertNotNull(result);
        verify(autorizacaoBancariaRepository, times(1)).save(autorizacao);
    }

    @Test
    void testSalvarAutorizacao_UsuarioNaoEncontrado() {
        // Arrange
        when(usuarioRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            autorizacaoBancariaService.salvarAutorizacao(
                    1L, BankApiService.BankType.MERCADO_PAGO, tokenResponse);
        });
    }

    @Test
    void testBuscarAutorizacoesAtivas() {
        // Arrange
        List<AutorizacaoBancaria> autorizacoes = Arrays.asList(autorizacao);
        when(autorizacaoBancariaRepository.findByUsuarioIdAndAtivoTrue(1L)).thenReturn(autorizacoes);

        // Act
        List<AutorizacaoBancaria> result = autorizacaoBancariaService.buscarAutorizacoesAtivas(1L);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(autorizacao, result.get(0));
    }

    @Test
    void testPossuiAutorizacaoAtiva_True() {
        // Arrange
        when(autorizacaoBancariaRepository.existsByUsuarioIdAndBancoAndAtivoTrue(1L, "MERCADO_PAGO"))
                .thenReturn(true);

        // Act
        boolean result = autorizacaoBancariaService.possuiAutorizacaoAtiva(
                1L, BankApiService.BankType.MERCADO_PAGO);

        // Assert
        assertTrue(result);
    }

    @Test
    void testPossuiAutorizacaoAtiva_False() {
        // Arrange
        when(autorizacaoBancariaRepository.existsByUsuarioIdAndBancoAndAtivoTrue(1L, "MERCADO_PAGO"))
                .thenReturn(false);

        // Act
        boolean result = autorizacaoBancariaService.possuiAutorizacaoAtiva(
                1L, BankApiService.BankType.MERCADO_PAGO);

        // Assert
        assertFalse(result);
    }

    @Test
    void testRenovarToken() {
        // Arrange
        Map<String, Object> newTokenResponse = new HashMap<>();
        newTokenResponse.put("access_token", "new_access_token");
        newTokenResponse.put("refresh_token", "new_refresh_token");
        newTokenResponse.put("expires_in", 7200);

        when(autorizacaoBancariaRepository.save(any(AutorizacaoBancaria.class))).thenReturn(autorizacao);

        // Act
        AutorizacaoBancaria result = autorizacaoBancariaService.renovarToken(autorizacao, newTokenResponse);

        // Assert
        assertNotNull(result);
        verify(autorizacaoBancariaRepository, times(1)).save(autorizacao);
    }

    @Test
    void testRevogarAutorizacao() {
        // Arrange
        when(autorizacaoBancariaRepository.findById(1L)).thenReturn(Optional.of(autorizacao));
        when(autorizacaoBancariaRepository.save(any(AutorizacaoBancaria.class))).thenReturn(autorizacao);

        // Act
        autorizacaoBancariaService.revogarAutorizacao(1L, 1L);

        // Assert
        assertFalse(autorizacao.getAtivo());
        verify(autorizacaoBancariaRepository, times(1)).save(autorizacao);
    }

    @Test
    void testRevogarAutorizacao_UsuarioDiferente() {
        // Arrange
        Usuario outroUsuario = new Usuario();
        outroUsuario.setId(2L);
        autorizacao.setUsuario(outroUsuario);

        when(autorizacaoBancariaRepository.findById(1L)).thenReturn(Optional.of(autorizacao));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            autorizacaoBancariaService.revogarAutorizacao(1L, 1L);
        });
    }
}

