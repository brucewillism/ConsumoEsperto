package com.consumoesperto.dashboard;

import com.consumoesperto.autonomy.AutonomyPreferenciaService;
import com.consumoesperto.autonomy.EdithSourceActions;
import com.consumoesperto.autonomy.QuietHours;
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
import com.consumoesperto.util.AppTimeZone;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Briefs WhatsApp das visões Mensal (semanal) e Geral (dia 28).
 * Números vêm só do {@link DashboardViewService}; E.D.I.T.H. narra, nunca calcula.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardBriefService {

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    private static final WeekFields ISO = WeekFields.ISO;

    private final DashboardBriefProperties properties;
    private final DashboardViewService dashboardViewService;
    private final NotificationOrchestratorService orchestrator;
    private final UsuarioRepository usuarioRepository;
    private final JarvisProtocolService jarvisProtocolService;
    private final CognitiveGatewaySelector cognitiveGatewaySelector;
    private final AutonomyPreferenciaService preferenciaService;

    public void enviarBriefsSemanais() {
        if (!properties.isWeeklyEnabled()) {
            return;
        }
        LocalDate hoje = AppTimeZone.hoje();
        for (Usuario usuario : usuarioRepository.findAll()) {
            if (usuario.getWhatsappNumero() == null || usuario.getWhatsappNumero().isBlank()) {
                continue;
            }
            try {
                enviarUm(usuario, DashboardViewMode.MONTHLY, hoje);
            } catch (Exception e) {
                log.warn("[DASHBOARD-BRIEF] semanal user {}: {}", usuario.getId(), e.getMessage());
            }
        }
    }

    public void enviarBriefsGerais() {
        if (!properties.isGeneralEnabled()) {
            return;
        }
        LocalDate hoje = AppTimeZone.hoje();
        for (Usuario usuario : usuarioRepository.findAll()) {
            if (usuario.getWhatsappNumero() == null || usuario.getWhatsappNumero().isBlank()) {
                continue;
            }
            try {
                enviarUm(usuario, DashboardViewMode.GENERAL, hoje);
            } catch (Exception e) {
                log.warn("[DASHBOARD-BRIEF] geral user {}: {}", usuario.getId(), e.getMessage());
            }
        }
    }

    public boolean enviarUm(Usuario usuario, DashboardViewMode mode, LocalDate referencia) {
        Long userId = usuario.getId();
        String hash = mode == DashboardViewMode.MONTHLY
            ? hashSemanal(userId, referencia)
            : hashGeral(userId, YearMonth.from(referencia));
        DashboardViewDTO view = dashboardViewService.montar(userId, mode);
        String vocativo = jarvisProtocolService.resolveVocative(userId, usuarioRepository);
        String template = mode == DashboardViewMode.MONTHLY
            ? templateMensal(vocativo, view)
            : templateGeral(vocativo, view);
        String mensagem = narrarSePossivel(userId, template);
        boolean silencioso = emHorarioSilencioso(userId);
        NotificacaoEventoTipo evento = mode == DashboardViewMode.MONTHLY
            ? NotificacaoEventoTipo.RESUMO_SEMANAL
            : NotificacaoEventoTipo.FORECAST_MENSAL;
        return orchestrator.solicitar(NotificacaoSolicitacao.builder()
            .usuarioId(userId)
            .evento(evento)
            .mensagem(mensagem)
            .hashEvento(hash)
            .digestLinha(mode == DashboardViewMode.MONTHLY ? "Brief semanal (visão mensal)" : "Brief dia 28 (visão geral)")
            .tituloWeb(mode == DashboardViewMode.MONTHLY ? "Visão mensal — J.A.R.V.I.S." : "Visão geral — J.A.R.V.I.S.")
            .entregaImediata(!silencioso)
            .build());
    }

    public static String hashSemanal(Long usuarioId, LocalDate ref) {
        int week = ref.get(ISO.weekOfWeekBasedYear());
        int year = ref.get(ISO.weekBasedYear());
        return "MONTHLY_WEEKLY_BRIEF:" + usuarioId + ":" + year + "-W" + String.format("%02d", week);
    }

    public static String hashGeral(Long usuarioId, YearMonth ym) {
        return "GENERAL_MONTHLY_BRIEF:" + usuarioId + ":" + ym;
    }

    String narrarSePossivel(Long usuarioId, String template) {
        if (!properties.isEdithNarrate()) {
            return template;
        }
        try {
            CognitiveResponse r = cognitiveGatewaySelector.dispatch(CognitiveRequest.builder()
                .usuarioId(usuarioId)
                .content("Narre este briefing financeiro em português, breve e claro. "
                    + "Não invente nem altere números — use apenas os valores do texto.\n\n" + template)
                .sourceAction(EdithSourceActions.FINANCIAL_BRIEF)
                .timeoutMs(properties.getEdithTimeoutMs())
                .awaitCompletion(true)
                .build());
            if (r != null && r.getResultText() != null && !r.getResultText().isBlank()
                && !"FAILED".equalsIgnoreCase(r.getStatus())) {
                return r.getResultText();
            }
        } catch (Exception e) {
            log.info("[DASHBOARD-BRIEF] E.D.I.T.H. indisponível, template determinístico user {}: {}",
                usuarioId, e.getMessage());
        }
        return template;
    }

    private boolean emHorarioSilencioso(Long usuarioId) {
        try {
            AutonomyPreferencia pref = preferenciaService.obterOuCriar(usuarioId);
            LocalTime start = pref.getSilenciosoInicio();
            LocalTime end = pref.getSilenciosoFim();
            return QuietHours.active(AppTimeZone.agora().toLocalTime(), start, end);
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private String templateMensal(String vocativo, DashboardViewDTO view) {
        Map<String, Object> m = view.getMetricas();
        Map<String, Object> c = view.getCompromissos();
        StringBuilder sb = new StringBuilder();
        sb.append("*Visão mensal — J.A.R.V.I.S.*\n");
        sb.append(vocativo).append(", o que pesa neste mês (").append(view.getPeriodo()).append("):\n\n");
        sb.append("Entrou: *").append(brl(m.get("receitasConfirmadas"))).append("*\n");
        sb.append("Saiu: *").append(brl(m.get("despesasConfirmadas"))).append("*\n");
        sb.append("Projeção do mês: *").append(brl(m.get("projecaoMes"))).append("*\n");
        sb.append("Fatura do mês: *").append(brl(valorBloco(c, "cartaoFatura"))).append("*\n");
        sb.append("Parcela do mês: *").append(brl(valorBloco(c, "emprestimos"))).append("*\n");
        sb.append("Safe-to-spend do mês: *").append(brl(m.get("safeToSpendMes"))).append("*\n");
        List<Map<String, String>> alertas = view.getAlertas();
        if (alertas != null && !alertas.isEmpty()) {
            sb.append("\nAlertas: ");
            sb.append(alertas.get(0).get("titulo"));
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String templateGeral(String vocativo, DashboardViewDTO view) {
        Map<String, Object> m = view.getMetricas();
        StringBuilder sb = new StringBuilder();
        sb.append("*Visão geral — J.A.R.V.I.S.*\n");
        sb.append(vocativo).append(", saúde financeira em ").append(view.getPeriodo()).append(":\n\n");
        sb.append("Ativos (contas + investimentos): *").append(brl(m.get("ativos"))).append("*\n");
        sb.append("Total em contas: *").append(brl(m.get("totalEmContas"))).append("*\n");
        sb.append("Total investido: *").append(brl(m.get("totalInvestido"))).append("*\n");
        sb.append("Passivos (saldo devedor): *").append(brl(m.get("passivos"))).append("*\n");
        sb.append("Patrimônio líquido: *").append(brl(m.get("patrimonioLiquido"))).append("*\n");
        sb.append("Dívida total: *").append(brl(m.get("dividaTotal"))).append("*\n");
        sb.append("Reservas: *").append(brl(m.get("reservas"))).append("*\n");
        sb.append("Score: *").append(m.getOrDefault("score", "—")).append("*\n");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static Object valorBloco(Map<String, Object> compromissos, String chave) {
        if (compromissos == null) {
            return BigDecimal.ZERO;
        }
        Object bloco = compromissos.get(chave);
        if (bloco instanceof Map<?, ?> map) {
            return map.get("total");
        }
        return BigDecimal.ZERO;
    }

    private static String brl(Object v) {
        if (v instanceof BigDecimal bd) {
            return BRL.format(bd);
        }
        if (v instanceof Number n) {
            return BRL.format(n);
        }
        return "R$ 0,00";
    }
}
