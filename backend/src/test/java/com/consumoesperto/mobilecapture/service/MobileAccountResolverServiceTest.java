package com.consumoesperto.mobilecapture.service;

import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.ContaBancariaRepository;
import com.consumoesperto.repository.MobileSourceMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MobileAccountResolverServiceTest {

    @Mock private MobileSourceMappingRepository mappingRepository;
    @Mock private CartaoCreditoRepository cartaoCreditoRepository;
    @Mock private ContaBancariaRepository contaBancariaRepository;

    private MobileAccountResolverService service;

    @BeforeEach
    void setup() {
        service = new MobileAccountResolverService(
            mappingRepository, cartaoCreditoRepository, contaBancariaRepository,
            new MerchantNormalizationService());
    }

    @Test
    void nomeUnicoResolveCartao() {
        CartaoCredito c = new CartaoCredito();
        c.setId(7L);
        c.setNome("Cartao Teste");
        when(mappingRepository.findByUsuarioIdAndEnabledTrueOrderByUpdatedAtDesc(1L)).thenReturn(List.of());
        when(cartaoCreditoRepository.findByUsuarioId(1L)).thenReturn(List.of(c));

        MobileAccountResolverService.ResolveOutcome out =
            service.resolveDetailed(1L, 9L, null, "Cartao Teste");
        assertFalse(out.needsReview());
        assertEquals(7L, out.account().orElseThrow().cartaoId());
    }

    @Test
    void doisCartoesComMesmoPadraoVaiParaReview() {
        CartaoCredito a = new CartaoCredito();
        a.setId(1L);
        a.setNome("Visa Teste");
        CartaoCredito b = new CartaoCredito();
        b.setId(2L);
        b.setNome("Visa Teste 2");
        when(mappingRepository.findByUsuarioIdAndEnabledTrueOrderByUpdatedAtDesc(1L)).thenReturn(List.of());
        when(cartaoCreditoRepository.findByUsuarioId(1L)).thenReturn(List.of(a, b));

        MobileAccountResolverService.ResolveOutcome out =
            service.resolveDetailed(1L, 9L, null, "Visa Teste");
        assertTrue(out.needsReview());
        assertTrue(out.account().isEmpty());
    }
}
