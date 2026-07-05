package com.consumoesperto.regressao;

import com.consumoesperto.repository.ContaBancariaRepository;
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
 * Regressão P0-6: a média móvel de 90 dias é NORMALIZADA para 30 dias (soma × 30 ÷ dias).
 * Na lógica antiga a soma de 3 meses era usada como se fosse renda mensal — 3× inflada.
 * A janela de 30 dias permanece idêntica (soma × 30 ÷ 30 = soma).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RendaMediaMovel90DiasRegressionTest {

    @Mock private RendaConfigRepository rendaConfigRepository;
    @Mock private ContaBancariaRepository contaBancariaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private TransacaoRepository transacaoRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private SalarioAutomaticoService salarioAutomaticoService;

    @InjectMocks private RendaConfigService service;

    @Test
    void janela90Dias_normalizaParaMediaMensal() {
        // R$ 9.000 recebidos ao longo de 90 dias → renda mensal equivalente = R$ 3.000
        when(transacaoRepository.sumReceitasConfirmadasPeriodo(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(new BigDecimal("9000.00"));

        BigDecimal media = service.calcularMediaMovelReal(1L, 90);

        assertEquals(0, media.compareTo(new BigDecimal("3000.00")),
            "lógica antiga devolvia 9000.00 (soma bruta da janela)");
    }

    @Test
    void janela30Dias_permaneceIdentica() {
        when(transacaoRepository.sumReceitasConfirmadasPeriodo(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(new BigDecimal("3000.00"));

        BigDecimal media = service.calcularMediaMovelReal(1L, 30);

        assertEquals(0, media.compareTo(new BigDecimal("3000.00")));
    }
}
