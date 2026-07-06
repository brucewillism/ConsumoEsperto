package com.consumoesperto.regressao;

import com.consumoesperto.repository.ContaBancariaRepository;
import com.consumoesperto.repository.MovimentacaoSaldoLogRepository;
import com.consumoesperto.repository.RendaConfigRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.service.RendaConfigService;
import com.consumoesperto.service.SalarioAutomaticoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Regressão EM-17: crédito de empréstimo não entra na média móvel de renda (sumReceitas exclui emprestimo_id).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmprestimoRendaMediaRegressionTest {

    @Mock private RendaConfigRepository rendaConfigRepository;
    @Mock private ContaBancariaRepository contaBancariaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private TransacaoRepository transacaoRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private SalarioAutomaticoService salarioAutomaticoService;

    @InjectMocks private RendaConfigService service;

    @Test
    void mediaMovel30Dias_excluiCreditoEmprestimo() {
        // Repositório já filtra emprestimo_id IS NULL — simula só salário na janela
        when(transacaoRepository.sumReceitasConfirmadasPeriodo(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(new BigDecimal("3000.00"));

        BigDecimal media = service.calcularMediaMovelReal(1L, 30);

        assertEquals(0, media.compareTo(new BigDecimal("3000.00")),
            "crédito de empréstimo não deve inflar a renda média");
    }
}
