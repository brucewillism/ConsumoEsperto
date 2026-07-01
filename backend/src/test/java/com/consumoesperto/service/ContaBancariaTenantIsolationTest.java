package com.consumoesperto.service;

import com.consumoesperto.exception.ResourceNotFoundException;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.ContaBancariaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/** Garante isolamento multi-tenant em contas bancárias (IDOR). */
@ExtendWith(MockitoExtension.class)
class ContaBancariaTenantIsolationTest {

    @Mock
    private ContaBancariaRepository contaBancariaRepository;

    @InjectMocks
    private ContaBancariaService contaBancariaService;

    @Test
    void usuarioB_naoAcessaContaDeUsuarioA() {
        when(contaBancariaRepository.findByIdAndUsuarioId(99L, 2L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> contaBancariaService.buscarEntidade(99L, 2L));
    }

    @Test
    void usuarioA_acessaPropriaConta() {
        ContaBancaria conta = new ContaBancaria();
        conta.setId(99L);
        Usuario u = new Usuario();
        u.setId(1L);
        conta.setUsuario(u);
        when(contaBancariaRepository.findByIdAndUsuarioId(99L, 1L)).thenReturn(Optional.of(conta));
        ContaBancaria found = contaBancariaService.buscarEntidade(99L, 1L);
        org.junit.jupiter.api.Assertions.assertEquals(99L, found.getId());
    }
}
