package com.consumoesperto.service;

import com.consumoesperto.config.WhatsappConexaoMonitorProperties;
import com.consumoesperto.dto.EvolutionPairingOutcomeDTO;
import com.consumoesperto.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsappPairingCodeServiceTest {

    @Mock private WhatsAppUserMappingService mappingService;
    @Mock private EvolutionPairingService pairingService;
    @Mock private EvolutionInstanceLifecycleService lifecycleService;

    private WhatsappPairingCodeService service;

    @BeforeEach
    void setup() {
        WhatsappConexaoMonitorProperties props = new WhatsappConexaoMonitorProperties();
        props.setPairingCodeTtlSegundos(90);
        service = new WhatsappPairingCodeService(mappingService, pairingService, lifecycleService, props);
    }

    @Test
    void devolveCodigoQuandoEvolutionResponde() {
        when(mappingService.normalize("(81) 99999-9999")).thenReturn("+5581999999999");
        when(pairingService.invokeInstanceConnect(1L)).thenReturn(
            EvolutionPairingOutcomeDTO.builder()
                .resolvedInstanceName("ce-u1")
                .pairingCode("ABCD1234")
                .alreadyConnected(false)
                .build()
        );
        when(pairingService.isInstanceConnectedForUser(1L)).thenReturn(false);

        var dto = service.gerar(1L, "(81) 99999-9999");
        assertEquals("ABCD1234", dto.getPairingCode());
        assertEquals("+5581999999999", dto.getNumeroNormalizado());
        assertEquals(90, dto.getValidadeSegundos());
        verify(mappingService).linkWhatsAppNumber(1L, "+5581999999999");
    }

    @Test
    void erroAmigavelQuandoEvolutionNaoDevolveCodigo() {
        when(mappingService.normalize(anyString())).thenReturn("+5581999999999");
        when(pairingService.invokeInstanceConnect(anyLong())).thenReturn(
            EvolutionPairingOutcomeDTO.builder()
                .resolvedInstanceName("ce-u1")
                .evolutionWarning("sem pairingCode")
                .alreadyConnected(false)
                .build()
        );
        when(pairingService.isInstanceConnectedForUser(anyLong())).thenReturn(false);

        var ex = assertThrows(
            WhatsappPairingCodeService.PairingCodeIndisponivelException.class,
            () -> service.gerar(1L, "5581999999999")
        );
        assertTrue(ex.getMessage().contains("sem pairingCode"));
    }
}

class WhatsappNumeroNormalizerTest {

    private WhatsAppUserMappingService mapping;

    @BeforeEach
    void setup() {
        mapping = new WhatsAppUserMappingService(org.mockito.Mockito.mock(UsuarioRepository.class));
    }

    @Test
    void normalizaComESemDdiEMascara() {
        assertEquals("+5581999999999", mapping.normalize("81999999999"));
        assertEquals("+5581999999999", mapping.normalize("5581999999999"));
        assertEquals("+5581999999999", mapping.normalize("+55 (81) 99999-9999"));
    }

    @Test
    void rejeitaVazio() {
        assertThrows(RuntimeException.class, () -> mapping.normalize(" "));
    }
}
