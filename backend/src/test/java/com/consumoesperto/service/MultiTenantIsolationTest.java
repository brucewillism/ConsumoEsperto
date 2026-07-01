package com.consumoesperto.service;

import com.consumoesperto.exception.ResourceNotFoundException;
import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.MetaFinanceira;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.ContaBancariaRepository;
import com.consumoesperto.repository.MetaFinanceiraRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/** Isolamento multi-tenant (IDOR) — entidades de maior risco. */
@ExtendWith(MockitoExtension.class)
class MultiTenantIsolationTest {

    @Mock private ContaBancariaRepository contaBancariaRepository;
    @Mock private CartaoCreditoRepository cartaoCreditoRepository;
    @Mock private MetaFinanceiraRepository metaFinanceiraRepository;

    @InjectMocks private ContaBancariaService contaBancariaService;
    @InjectMocks private CartaoCreditoService cartaoCreditoService;
    @InjectMocks private MetaFinanceiraService metaFinanceiraService;

    private Usuario usuarioA;

    @BeforeEach
    void setUp() {
        usuarioA = new Usuario();
        usuarioA.setId(1L);
    }

    @Test
    void contaBancaria_usuarioB_naoAcessaContaDeA() {
        when(contaBancariaRepository.findByIdAndUsuarioId(99L, 2L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> contaBancariaService.buscarEntidade(99L, 2L));
    }

    @Test
    void cartaoCredito_usuarioB_naoAcessaCartaoDeA() {
        when(cartaoCreditoRepository.findByIdAndUsuarioId(88L, 2L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> cartaoCreditoService.buscarPorId(88L, 2L));
    }

    @Test
    void metaFinanceira_usuarioB_naoVeMetaDeA() {
        when(metaFinanceiraRepository.findByIdAndUsuarioId(77L, 2L)).thenReturn(Optional.empty());
        assertTrue(metaFinanceiraService.buscar(77L, 2L).isEmpty());
    }

    @Test
    void contaBancaria_usuarioA_acessaPropriaConta() {
        ContaBancaria conta = new ContaBancaria();
        conta.setId(99L);
        conta.setUsuario(usuarioA);
        when(contaBancariaRepository.findByIdAndUsuarioId(99L, 1L)).thenReturn(Optional.of(conta));
        assertEquals(99L, contaBancariaService.buscarEntidade(99L, 1L).getId());
    }
}
