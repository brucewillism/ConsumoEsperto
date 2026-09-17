package com.consumoesperto.dashboard;

import com.consumoesperto.autonomy.AutonomyPreferenciaService;
import com.consumoesperto.autonomy.EdithSourceActions;
import com.consumoesperto.config.DashboardBriefProperties;
import com.consumoesperto.dto.NotificacaoSolicitacao;
import com.consumoesperto.edith.CognitiveGatewaySelector;
import com.consumoesperto.edith.CognitiveRequest;
import com.consumoesperto.edith.CognitiveResponse;
import com.consumoesperto.model.AutonomyPreferencia;
import com.consumoesperto.model.NotificacaoEventoTipo;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.service.JarvisProtocolService;
import com.consumoesperto.service.NotificationOrchestratorService;
import com.consumoesperto.service.ProactiveFinancialJobs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardBriefServiceTest {

    @Mock private DashboardViewService dashboardViewService;
    @Mock private NotificationOrchestratorService orchestrator;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private JarvisProtocolService jarvisProtocolService;
    @Mock private CognitiveGatewaySelector cognitiveGatewaySelector;
    @Mock private AutonomyPreferenciaService preferenciaService;

    private DashboardBriefProperties properties;
    private DashboardBriefService service;

    @BeforeEach
    void setup() {
        properties = new DashboardBriefProperties();
        properties.setEdithNarrate(true);
        service = new DashboardBriefService(
            properties, dashboardViewService, orchestrator, usuarioRepository,
            jarvisProtocolService, cognitiveGatewaySelector, preferenciaService
        );
        when(jarvisProtocolService.resolveVocative(anyLong(), any())).thenReturn("Senhor");
        AutonomyPreferencia pref = new AutonomyPreferencia();
        pref.setSilenciosoInicio(LocalTime.of(22, 0));
        pref.setSilenciosoFim(LocalTime.of(7, 0));
        when(preferenciaService.obterOuCriar(1L)).thenReturn(pref);
        when(dashboardViewService.montar(anyLong(), any())).thenReturn(DashboardViewDTO.builder()
            .viewMode("MONTHLY")
            .periodo("2026-09")
            .metricas(Map.of(
                "receitasConfirmadas", new java.math.BigDecimal("1000"),
                "despesasConfirmadas", new java.math.BigDecimal("400"),
                "projecaoMes", new java.math.BigDecimal("2000"),
                "safeToSpendMes", new java.math.BigDecimal("300"),
                "ativos", new java.math.BigDecimal("5000"),
                "passivos", new java.math.BigDecimal("1500"),
                "patrimonioLiquido", new java.math.BigDecimal("3500"),
                "dividaTotal", new java.math.BigDecimal("1800"),
                "reservas", new java.math.BigDecimal("800"),
                "score", 720
            ))
            .compromissos(Map.of(
                "cartaoFatura", Map.of("total", new java.math.BigDecimal("200")),
                "emprestimos", Map.of("total", new java.math.BigDecimal("150"))
            ))
            .alertas(List.of())
            .build());
    }

    @Test
    void hashSemanalUsaSemanaIso() {
        LocalDate d = LocalDate.of(2026, 9, 14);
        assertEquals("MONTHLY_WEEKLY_BRIEF:7:2026-W38", DashboardBriefService.hashSemanal(7L, d));
        assertEquals(DashboardBriefService.hashSemanal(7L, d), DashboardBriefService.hashSemanal(7L, d.plusDays(2)));
        assertFalse(DashboardBriefService.hashSemanal(7L, d).equals(
            DashboardBriefService.hashSemanal(7L, d.plusWeeks(1))));
    }

    @Test
    void hashGeralUsaCompetenciaMensal() {
        assertEquals("GENERAL_MONTHLY_BRIEF:7:2026-09",
            DashboardBriefService.hashGeral(7L, YearMonth.of(2026, 9)));
    }

    @Test
    void edithOfflineUsaTemplateDeterministico() {
        when(cognitiveGatewaySelector.dispatch(any())).thenThrow(new RuntimeException("offline"));
        String out = service.narrarSePossivel(1L, "Projeção do mês: R$ 10,00");
        assertEquals("Projeção do mês: R$ 10,00", out);
    }

    @Test
    void edithNarraSemCalcular() {
        when(cognitiveGatewaySelector.dispatch(any())).thenReturn(CognitiveResponse.builder()
            .status("OK")
            .resultText("Narrativa com os mesmos números.")
            .build());
        String out = service.narrarSePossivel(1L, "template");
        assertEquals("Narrativa com os mesmos números.", out);
        ArgumentCaptor<CognitiveRequest> cap = ArgumentCaptor.forClass(CognitiveRequest.class);
        verify(cognitiveGatewaySelector).dispatch(cap.capture());
        assertEquals(EdithSourceActions.FINANCIAL_BRIEF, cap.getValue().getSourceAction());
    }

    @Test
    void enviarUmReusaOrquestradorComHashIdempotente() {
        when(orchestrator.solicitar(any())).thenReturn(true);
        Usuario u = new Usuario();
        u.setId(1L);
        u.setWhatsappNumero("+5511999999999");
        boolean ok = service.enviarUm(u, DashboardViewMode.MONTHLY, LocalDate.of(2026, 9, 14));
        assertTrue(ok);
        ArgumentCaptor<NotificacaoSolicitacao> cap = ArgumentCaptor.forClass(NotificacaoSolicitacao.class);
        verify(orchestrator).solicitar(cap.capture());
        assertEquals(NotificacaoEventoTipo.RESUMO_SEMANAL, cap.getValue().getEvento());
        assertEquals(DashboardBriefService.hashSemanal(1L, LocalDate.of(2026, 9, 14)), cap.getValue().getHashEvento());
        assertTrue(cap.getValue().getMensagem().contains("Visão mensal"));
        assertFalse(cap.getValue().getMensagem().contains("Saldo devedor"));
    }

    @Test
    void segundaChamadaComMesmoHashNaoReenvia() {
        when(orchestrator.solicitar(any())).thenReturn(true, false);
        Usuario u = new Usuario();
        u.setId(1L);
        u.setWhatsappNumero("+5511999999999");
        assertTrue(service.enviarUm(u, DashboardViewMode.GENERAL, LocalDate.of(2026, 9, 28)));
        assertFalse(service.enviarUm(u, DashboardViewMode.GENERAL, LocalDate.of(2026, 9, 28)));
        ArgumentCaptor<NotificacaoSolicitacao> cap = ArgumentCaptor.forClass(NotificacaoSolicitacao.class);
        verify(orchestrator, times(2)).solicitar(cap.capture());
        assertEquals(cap.getAllValues().get(0).getHashEvento(), cap.getAllValues().get(1).getHashEvento());
        assertEquals(NotificacaoEventoTipo.FORECAST_MENSAL, cap.getAllValues().get(0).getEvento());
        assertTrue(cap.getAllValues().get(0).getMensagem().contains("Patrimônio líquido"));
    }

    @Test
    void jobsUsamTimezoneSaoPaulo() throws Exception {
        Method semanal = ProactiveFinancialJobs.class.getMethod("enviarResumoSemanal");
        Scheduled s = semanal.getAnnotation(Scheduled.class);
        assertEquals("America/Sao_Paulo", s.zone());
        assertTrue(s.cron().contains("weekly-cron"));

        Method geral = ProactiveFinancialJobs.class.getMethod("enviarBriefGeralDia28");
        Scheduled g = geral.getAnnotation(Scheduled.class);
        assertEquals("America/Sao_Paulo", g.zone());
        assertTrue(g.cron().contains("general-cron"));
        assertTrue(g.cron().contains("28"));
    }

    @Test
    void flagsDesligadasNaoDisparam() {
        properties.setWeeklyEnabled(false);
        properties.setGeneralEnabled(false);
        service.enviarBriefsSemanais();
        service.enviarBriefsGerais();
        verify(usuarioRepository, never()).findAll();
    }
}
