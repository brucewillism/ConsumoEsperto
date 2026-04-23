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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para BankSynchronizationService
 */
@ExtendWith(MockitoExtension.class)
class BankSynchronizationServiceTest {

    @Mock
    private AutorizacaoBancariaService autorizacaoBancariaService;

    @Mock
    private AutorizacaoBancariaRepository autorizacaoBancariaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private MercadoPagoBankService mercadoPagoBankService;

    @Mock
    private NubankBankService nubankBankService;

    @Mock
    private ItauBankService itauBankService;

    @Mock
    private InterBankService interBankService;

    @InjectMocks
    private BankSynchronizationService bankSynchronizationService;

    private Usuario usuario;
    private AutorizacaoBancaria autorizacao;

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
    }

    @Test
    void testGetSyncStatus_ComAutorizacoes() {
        // Arrange
        List<AutorizacaoBancaria> autorizacoes = Arrays.asList(autorizacao);
        when(autorizacaoBancariaService.buscarAutorizacoesAtivas(1L)).thenReturn(autorizacoes);

        // Act
        Map<String, Object> result = bankSynchronizationService.getSyncStatus(1L);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.get("usuario_id"));
        assertNotNull(result.get("status_geral"));
        assertNotNull(result.get("bancos"));
        assertTrue(result.containsKey("ultima_sincronizacao"));
        assertTrue(result.containsKey("total_bancos_conectados"));
    }

    @Test
    void testGetSyncStatus_SemAutorizacoes() {
        // Arrange
        when(autorizacaoBancariaService.buscarAutorizacoesAtivas(1L)).thenReturn(Collections.emptyList());

        // Act
        Map<String, Object> result = bankSynchronizationService.getSyncStatus(1L);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.get("usuario_id"));
        assertEquals("SEM_CONEXOES", result.get("status_geral"));
        assertNull(result.get("ultima_sincronizacao"));
    }

    @Test
    void testGetSyncStatus_ComErro() {
        // Arrange
        when(autorizacaoBancariaService.buscarAutorizacoesAtivas(1L))
                .thenThrow(new RuntimeException("Erro ao buscar autorizações"));

        // Act
        Map<String, Object> result = bankSynchronizationService.getSyncStatus(1L);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.get("usuario_id"));
        assertEquals("ERRO", result.get("status_geral"));
        assertTrue(result.containsKey("erro"));
    }

    @Test
    void testGetSyncStatus_ComAutorizacaoExpirada() {
        // Arrange
        autorizacao.setDataExpiracao(LocalDateTime.now().minusHours(1)); // Expirada
        List<AutorizacaoBancaria> autorizacoes = Arrays.asList(autorizacao);
        when(autorizacaoBancariaService.buscarAutorizacoesAtivas(1L)).thenReturn(autorizacoes);

        // Act
        Map<String, Object> result = bankSynchronizationService.getSyncStatus(1L);

        // Assert
        assertNotNull(result);
        @SuppressWarnings("unchecked")
        Map<String, Object> bancos = (Map<String, Object>) result.get("bancos");
        assertNotNull(bancos);
        @SuppressWarnings("unchecked")
        Map<String, Object> bancoStatus = (Map<String, Object>) bancos.get("MERCADO_PAGO");
        assertNotNull(bancoStatus);
        assertTrue((Boolean) bancoStatus.get("precisa_renovacao"));
    }
}

