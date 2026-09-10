package com.consumoesperto.service;

import com.consumoesperto.config.WhatsappConexaoMonitorProperties;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.model.UsuarioAiConfig;
import com.consumoesperto.model.WhatsappConexaoStatus;
import com.consumoesperto.repository.UsuarioAiConfigRepository;
import com.consumoesperto.repository.WhatsappConexaoStatusRepository;
import com.consumoesperto.repository.WhatsappConexaoTransicaoRepository;
import com.consumoesperto.util.AppTimeZone;
import com.consumoesperto.whatsapp.WhatsappConexaoEstados;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WhatsappConexaoMonitorServiceTest {

    @Mock private WhatsappConexaoStatusRepository statusRepository;
    @Mock private WhatsappConexaoTransicaoRepository transicaoRepository;
    @Mock private UsuarioAiConfigRepository usuarioAiConfigRepository;
    @Mock private EvolutionPairingService pairingService;
    @Mock private EvolutionInstanceSettingsService settingsService;
    @Mock private EvolutionInstanceLifecycleService lifecycleService;
    @Mock private EvolutionSessionMetricsService metricsService;
    @Mock private EvolutionWaSessionRegistry sessionRegistry;
    @Mock private AlertaOperacionalService alertaOperacionalService;

    private WhatsappConexaoMonitorProperties props;
    private WhatsappConexaoMonitorService service;
    private final AtomicReference<WhatsappConexaoStatus> stored = new AtomicReference<>();

    @BeforeEach
    void setup() {
        props = new WhatsappConexaoMonitorProperties();
        props.setEnabled(true);
        props.setMaxTentativas(3);
        props.setAlertaAposTentativas(2);
        props.setAlertaAposMinutos(15);
        props.setBackoffMinutos(new int[] {1, 2, 5, 15, 30});
        service = new WhatsappConexaoMonitorService(
            props,
            statusRepository,
            transicaoRepository,
            usuarioAiConfigRepository,
            pairingService,
            settingsService,
            lifecycleService,
            metricsService,
            sessionRegistry,
            alertaOperacionalService
        );
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "http://localhost:14200");

        when(statusRepository.save(any(WhatsappConexaoStatus.class))).thenAnswer(inv -> {
            WhatsappConexaoStatus s = inv.getArgument(0);
            stored.set(s);
            return s;
        });
        when(statusRepository.findById(7L)).thenAnswer(inv -> Optional.ofNullable(stored.get()));
        when(transicaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRegistry.isUserDisconnected(7L)).thenReturn(false);
        when(pairingService.resolvedInstanceDisplayName(7L)).thenReturn("ce-u7");
        when(transicaoRepository.findTop20ByUsuarioIdOrderByVerificadoEmDesc(7L)).thenReturn(List.of());
    }

    @Test
    void transicaoCloseReconectaEVoltaOpen() {
        when(pairingService.fetchConnectionStateForUser(7L)).thenReturn(Optional.of("close"));
        when(pairingService.attemptSessionReconnect("ce-u7")).thenReturn(true);

        service.verificarUsuario(7L, "poll");

        verify(pairingService).attemptSessionReconnect("ce-u7");
        verify(settingsService, never()).restartInstance(anyString());
        assertEquals(WhatsappConexaoEstados.OPEN, stored.get().getEstado());
        assertEquals(0, stored.get().getTentativasReconexao());
        verify(transicaoRepository, times(2)).save(any());
    }

    @Test
    void backoffRespeitadoNaoTentaDeNovoAntesDaHora() {
        WhatsappConexaoStatus cur = baseStatus(WhatsappConexaoEstados.CLOSE, 1);
        cur.setProximaTentativaEm(AppTimeZone.agora().plusMinutes(10));
        stored.set(cur);
        when(pairingService.fetchConnectionStateForUser(7L)).thenReturn(Optional.of("close"));

        service.verificarUsuario(7L, "poll");

        verify(pairingService, never()).attemptSessionReconnect(anyString());
    }

    @Test
    void tetoDeTentativasNaoReconectaEAlerta() {
        WhatsappConexaoStatus cur = baseStatus(WhatsappConexaoEstados.CLOSE, 3);
        cur.setProximaTentativaEm(null);
        stored.set(cur);
        when(pairingService.fetchConnectionStateForUser(7L)).thenReturn(Optional.of("close"));

        service.verificarUsuario(7L, "poll");

        verify(pairingService, never()).attemptSessionReconnect(anyString());
        verify(alertaOperacionalService).alertar(
            eq(AlertaOperacionalService.TIPO_WHATSAPP_DESCONECTADO),
            anyString(),
            anyString(),
            eq(true)
        );
        assertTrue(stored.get().isAlertaDesconexaoEnviado());
    }

    @Test
    void alertaAposNFalhasERecuperacaoEnviaEmailDeVolta() {
        when(pairingService.fetchConnectionStateForUser(7L)).thenReturn(Optional.of("close"));
        when(pairingService.attemptSessionReconnect("ce-u7")).thenReturn(false);
        when(settingsService.restartInstance("ce-u7")).thenReturn(true);

        service.verificarUsuario(7L, "poll");
        stored.get().setProximaTentativaEm(AppTimeZone.agora().minusMinutes(1));
        service.verificarUsuario(7L, "poll");

        verify(alertaOperacionalService).alertar(
            eq(AlertaOperacionalService.TIPO_WHATSAPP_DESCONECTADO),
            anyString(),
            anyString(),
            eq(true)
        );

        when(pairingService.attemptSessionReconnect("ce-u7")).thenReturn(true);
        stored.get().setProximaTentativaEm(AppTimeZone.agora().minusMinutes(1));
        service.verificarUsuario(7L, "poll");

        verify(alertaOperacionalService).alertar(
            eq(AlertaOperacionalService.TIPO_WHATSAPP_RECUPERADO),
            anyString(),
            anyString(),
            eq(false)
        );
        assertFalse(stored.get().isAlertaDesconexaoEnviado());
        assertEquals(WhatsappConexaoEstados.OPEN, stored.get().getEstado());
    }

    @Test
    void webhookCloseDisparaReconexao() {
        Usuario u = new Usuario();
        u.setId(7L);
        UsuarioAiConfig cfg = new UsuarioAiConfig();
        cfg.setUsuario(u);
        cfg.setEvolutionInstanceName("ce-u7");
        when(usuarioAiConfigRepository.findByEvolutionInstanceNameIgnoreCase("ce-u7"))
            .thenReturn(Optional.of(cfg));
        when(pairingService.attemptSessionReconnect("ce-u7")).thenReturn(true);

        service.aoEventoConexao("ce-u7", "close");

        verify(pairingService).attemptSessionReconnect("ce-u7");
        ArgumentCaptor<WhatsappConexaoStatus> cap = ArgumentCaptor.forClass(WhatsappConexaoStatus.class);
        verify(statusRepository, org.mockito.Mockito.atLeastOnce()).save(cap.capture());
        assertEquals(WhatsappConexaoEstados.OPEN, stored.get().getEstado());
    }

    private static WhatsappConexaoStatus baseStatus(String estado, int tentativas) {
        WhatsappConexaoStatus s = new WhatsappConexaoStatus();
        s.setUsuarioId(7L);
        s.setInstanceName("ce-u7");
        s.setEstado(estado);
        s.setEstadoDesde(LocalDateTime.now().minusMinutes(20));
        s.setVerificadoEm(LocalDateTime.now());
        s.setTentativasReconexao(tentativas);
        s.setAlertaDesconexaoEnviado(false);
        return s;
    }
}
