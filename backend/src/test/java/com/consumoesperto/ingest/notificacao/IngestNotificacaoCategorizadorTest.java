package com.consumoesperto.ingest.notificacao;

import com.consumoesperto.mobilecapture.service.MerchantCategoryRuleService;
import com.consumoesperto.service.jarvis.CategoriaCorrecaoMemoriaService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IngestNotificacaoCategorizadorTest {

    @Test
    void priorizaMemoriaCorrecaoSobreRegraDeMerchant() {
        CategoriaCorrecaoMemoriaService correcao = mock(CategoriaCorrecaoMemoriaService.class);
        MerchantCategoryRuleService regras = mock(MerchantCategoryRuleService.class);
        when(correcao.sugerirCategoriaPorCorrecao(1L, "PADARIA DO ZE")).thenReturn(Optional.of(77L));
        when(regras.match(1L, "PADARIA DO ZE")).thenReturn(
            Optional.of(new MerchantCategoryRuleService.CategoryMatch(88L, BigDecimal.ONE, "RULE")));

        IngestNotificacaoCategorizador cat = new IngestNotificacaoCategorizador(correcao, regras);
        assertEquals(Optional.of(77L), cat.sugerir(1L, "PADARIA DO ZE"));
    }
}
