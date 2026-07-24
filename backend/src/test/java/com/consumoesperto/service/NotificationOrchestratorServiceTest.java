package com.consumoesperto.service;

import com.consumoesperto.dto.NotificacaoSolicitacao;
import com.consumoesperto.model.NotificacaoCategoria;
import com.consumoesperto.model.NotificacaoCanalEntrega;
import com.consumoesperto.model.NotificacaoDigestBuffer;
import com.consumoesperto.model.NotificacaoEnviada;
import com.consumoesperto.model.NotificacaoEventoTipo;
import com.consumoesperto.repository.NotificacaoDigestBufferRepository;
import com.consumoesperto.repository.NotificacaoEnviadaRepository;
import com.consumoesperto.util.AppTimeZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationOrchestratorServiceTest {

    @Mock private NotificacaoEnviadaRepository enviadaRepository;
    @Mock private NotificacaoDigestBufferRepository digestBufferRepository;
    @Mock private JarvisNotificacaoPreferenciasService preferenciasService;
    @Mock private WhatsAppDeliveryService whatsAppDeliveryService;
    @Mock private WebNotificationDeliveryService webNotificationDeliveryService;

    private NotificationOrchestratorService service;

    @BeforeEach
    void setUp() {
        service = new NotificationOrchestratorService(
            enviadaRepository, digestBufferRepository, preferenciasService,
            whatsAppDeliveryService, webNotificationDeliveryService);
        when(preferenciasService.estaAtiva(anyLong(), any())).thenReturn(true);
    }

    @Test
    void criticaEnviaSemCooldown() {
        when(preferenciasService.obterCanalEntrega(1L)).thenReturn(NotificacaoCanalEntrega.WHATSAPP);
        when(enviadaRepository.existsByUsuarioIdAndHashEvento(any(), any())).thenReturn(false);
        when(digestBufferRepository.existsByUsuarioIdAndHashEvento(any(), any())).thenReturn(false);
        when(whatsAppDeliveryService.enviar(1L, "alerta")).thenReturn(true);

        boolean ok = service.solicitar(NotificacaoSolicitacao.builder()
            .usuarioId(1L)
            .evento(NotificacaoEventoTipo.SALDO_NEGATIVO_PREVISTO)
            .mensagem("alerta")
            .hashEvento("hash1")
            .build());

        assertTrue(ok);
        verify(enviadaRepository).save(any(NotificacaoEnviada.class));
    }

    @Test
    void importanteRespeitaCooldown() {
        when(enviadaRepository.existsByUsuarioIdAndHashEvento(any(), any())).thenReturn(false);
        when(digestBufferRepository.existsByUsuarioIdAndHashEvento(any(), any())).thenReturn(false);
        when(enviadaRepository.findUltimoEnvioPorCategoria(1L, NotificacaoCategoria.IMPORTANTE))
            .thenReturn(Optional.of(LocalDateTime.now().minusHours(2)));

        boolean ok = service.solicitar(NotificacaoSolicitacao.builder()
            .usuarioId(1L)
            .evento(NotificacaoEventoTipo.ASSINATURA_PROXIMA)
            .mensagem("assinatura")
            .hashEvento("hash2")
            .build());

        assertFalse(ok);
        verify(whatsAppDeliveryService, never()).enviar(any(), any());
    }

    @Test
    void informativaAgrupaDuasNoMesmoDia() {
        LocalDate hoje = AppTimeZone.hoje();
        when(preferenciasService.obterCanalEntrega(1L)).thenReturn(NotificacaoCanalEntrega.WHATSAPP);
        when(enviadaRepository.existsByUsuarioIdAndHashEvento(any(), any())).thenReturn(false);
        when(digestBufferRepository.existsByUsuarioIdAndHashEvento(any(), any())).thenReturn(false);
        when(digestBufferRepository.findByUsuarioIdAndDataRefOrderByCriadoEmAsc(1L, hoje))
            .thenReturn(List.of(item("Score: 76")))
            .thenReturn(List.of(item("Score: 76"), item("Forecast: 87%")));
        when(enviadaRepository.findUltimoEnvioPorCategoria(1L, NotificacaoCategoria.INFORMATIVA))
            .thenReturn(Optional.empty());
        when(whatsAppDeliveryService.enviar(eq(1L), any())).thenReturn(true);

        service.solicitar(NotificacaoSolicitacao.builder()
            .usuarioId(1L)
            .evento(NotificacaoEventoTipo.SCORE_MENSAL)
            .mensagem("score")
            .digestLinha("Score: 76")
            .hashEvento("s1")
            .build());

        boolean ok = service.solicitar(NotificacaoSolicitacao.builder()
            .usuarioId(1L)
            .evento(NotificacaoEventoTipo.FORECAST_MENSAL)
            .mensagem("forecast")
            .digestLinha("Forecast: 87%")
            .hashEvento("s2")
            .build());

        assertTrue(ok);
        ArgumentCaptor<String> msgCap = ArgumentCaptor.forClass(String.class);
        verify(whatsAppDeliveryService).enviar(eq(1L), msgCap.capture());
        assertTrue(msgCap.getValue().contains("Resumo ConsumoEsperto"));
        assertTrue(msgCap.getValue().contains("Score: 76"));
    }

    @Test
    void duplicadaPorHashNaoEnvia() {
        when(enviadaRepository.existsByUsuarioIdAndHashEvento(1L, "dup")).thenReturn(true);

        boolean ok = service.solicitar(NotificacaoSolicitacao.builder()
            .usuarioId(1L)
            .evento(NotificacaoEventoTipo.RESUMO_SEMANAL)
            .mensagem("x")
            .hashEvento("dup")
            .build());

        assertFalse(ok);
        verify(digestBufferRepository, never()).save(any());
    }

    private static NotificacaoDigestBuffer item(String linha) {
        NotificacaoDigestBuffer b = new NotificacaoDigestBuffer();
        b.setLinhaDigest(linha);
        b.setMensagemCompleta(linha);
        return b;
    }
}
