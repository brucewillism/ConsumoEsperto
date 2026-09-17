package com.consumoesperto.autonomy;

import com.consumoesperto.dto.FaturaDTO;
import com.consumoesperto.service.FaturaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiveInvoiceProjectionServiceTest {

    @Mock private FaturaService faturaService;
    @InjectMocks private LiveInvoiceProjectionService service;

    @Test
    void pendenteApareceNoProjetadoSemMisturarComConfirmado() {
        FaturaDTO dto = new FaturaDTO();
        dto.setId(1L);
        dto.setNomeCartao("Cartao Teste");
        dto.setPaga(false);
        dto.setValorTotal(BigDecimal.ZERO);
        dto.setValorConfirmado(BigDecimal.ZERO);
        dto.setValorPendente(new BigDecimal("89.90"));
        dto.setValorProjetado(new BigDecimal("89.90"));
        dto.setDataFechamento(LocalDateTime.now().plusDays(10));
        when(faturaService.buscarPorUsuarioId(1L)).thenReturn(List.of(dto));

        List<Map<String, Object>> rows = service.atuais(1L);
        assertEquals(1, rows.size());
        assertEquals(0, new BigDecimal("0").compareTo((BigDecimal) rows.get(0).get("valorConfirmado")));
        assertEquals(0, new BigDecimal("89.90").compareTo((BigDecimal) rows.get(0).get("valorPendente")));
        assertEquals(0, new BigDecimal("89.90").compareTo((BigDecimal) rows.get(0).get("valorProjetado")));
    }
}
