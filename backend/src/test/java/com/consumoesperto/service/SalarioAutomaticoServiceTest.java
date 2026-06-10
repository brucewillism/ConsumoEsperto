package com.consumoesperto.service;

import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.RendaConfig;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.ContaBancariaRepository;
import com.consumoesperto.repository.RendaConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalarioAutomaticoServiceTest {

    @Mock
    private RendaConfigRepository rendaConfigRepository;
    @Mock
    private ContaBancariaRepository contaBancariaRepository;

    @InjectMocks
    private SalarioAutomaticoService salarioAutomaticoService;

    @Test
    void resolverDiaPagamento_usa30QuandoNaoConfigurado() {
        assertEquals(30, SalarioAutomaticoService.resolverDiaPagamento(null));
        assertEquals(30, SalarioAutomaticoService.resolverDiaPagamento(new RendaConfig()));
    }

    @Test
    void resolverContaDestinoSalario_prefereContaItauNoNome() {
        Long usuarioId = 7L;
        ContaBancaria nubank = conta(1L, "Nubank", usuarioId);
        ContaBancaria itau = conta(2L, "Itaú Conta Corrente", usuarioId);

        when(contaBancariaRepository.findByUsuarioIdAndAtivaTrueOrderByPadraoDescNomeAsc(usuarioId))
            .thenReturn(List.of(nubank, itau));

        Optional<Long> destino = salarioAutomaticoService.resolverContaDestinoSalario(null, usuarioId);

        assertTrue(destino.isPresent());
        assertEquals(2L, destino.get());
    }

    @Test
    void resolverContaDestinoSalario_usaContaConfiguradaNaRenda() {
        Long usuarioId = 9L;
        ContaBancaria configurada = conta(55L, "Bradesco", usuarioId);
        RendaConfig cfg = new RendaConfig();
        cfg.setContaBancaria(configurada);

        when(contaBancariaRepository.findByIdAndUsuarioId(55L, usuarioId))
            .thenReturn(Optional.of(configurada));

        Optional<Long> destino = salarioAutomaticoService.resolverContaDestinoSalario(cfg, usuarioId);

        assertTrue(destino.isPresent());
        assertEquals(55L, destino.get());
    }

    private static ContaBancaria conta(Long id, String nome, Long usuarioId) {
        Usuario u = new Usuario();
        u.setId(usuarioId);
        ContaBancaria c = new ContaBancaria();
        c.setId(id);
        c.setNome(nome);
        c.setUsuario(u);
        c.setAtiva(true);
        return c;
    }
}
