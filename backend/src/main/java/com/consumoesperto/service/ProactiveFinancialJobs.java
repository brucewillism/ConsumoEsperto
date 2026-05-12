package com.consumoesperto.service;

import com.consumoesperto.dto.OrcamentoDTO;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProactiveFinancialJobs {

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    private final UsuarioRepository usuarioRepository;
    private final TransacaoRepository transacaoRepository;
    private final RecurringExpenseDetectionService recurringExpenseDetectionService;
    private final WhatsAppNotificationService whatsAppNotificationService;
    private final OrcamentoService orcamentoService;
    private final ForecastFinanceiroService forecastFinanceiroService;
    private final SaldoService saldoService;
    private final ScoreService scoreService;
    private final FaturaRepository faturaRepository;
    private final GrupoFamiliarService grupoFamiliarService;
    private final OpenAiService openAiService;
    private final JarvisProtocolService jarvisProtocolService;
    private final PrevisaoFluxoCaixaService previsaoFluxoCaixaService;

    @Scheduled(cron = "0 0 8 * * *", zone = "America/Sao_Paulo")
    @Transactional(readOnly = true)
    public void alertarRecorrenciasComVencimentoProximo() {
        LocalDate alvo = LocalDate.now().plusDays(2);
        for (Usuario usuario : usuarioRepository.findAll()) {
            if (usuario.getWhatsappNumero() == null || usuario.getWhatsappNumero().isBlank()) {
                continue;
            }
            List<RecurringExpenseDetectionService.RecurringExpense> vencendo = recurringExpenseDetectionService
                .detectar(usuario.getId())
                .stream()
                .filter(r -> r.proximaData().equals(alvo))
                .collect(Collectors.toList());
            for (RecurringExpenseDetectionService.RecurringExpense r : vencendo) {
                String vocativo = jarvisProtocolService.resolveVocative(usuario.getId(), usuarioRepository);
                String msg = jarvisProtocolService.proativoContaRecorrenteVencimento(vocativo, r.nome(), BRL.format(r.valorMedio()));
                whatsAppNotificationService.enviarParaUsuario(usuario.getId(), msg);
            }
            saldoService.analisarDinheiroParado(usuario.getId())
                .ifPresent(a -> {
                    String v = jarvisProtocolService.resolveVocative(usuario.getId(), usuarioRepository);
                    String m = jarvisProtocolService.proativoAuditoriaLiquidez(
                        v,
                        BRL.format(a.saldoDisponivel()),
                        BRL.format(a.valorAplicavel()),
                        BRL.format(a.ganhoEstimado()),
                        a.vencimentoFatura(),
                        a.vencimentoFatura().minusDays(1).getDayOfMonth()
                    );
                    whatsAppNotificationService.enviarParaUsuario(usuario.getId(), m);
                });
            if (!faturaRepository.findVencidasByUsuarioId(usuario.getId(), LocalDateTime.now()).isEmpty()) {
                scoreService.registrarEvento(usuario.getId(), ScoreService.EventoScore.FATURA_VENCIDA,
                    "Fatura vencida detectada no monitoramento diário");
            }
        }
    }

    /** Sentinela — dia 5: relatório de disponibilidade real após obrigações. */
    @Scheduled(cron = "0 30 9 5 * *", zone = "America/Sao_Paulo")
    @Transactional(readOnly = true)
    public void enviarDisponibilidadeRealDiaCinco() {
        for (Usuario usuario : usuarioRepository.findAll()) {
            if (usuario.getWhatsappNumero() == null || usuario.getWhatsappNumero().isBlank()) {
                continue;
            }
            try {
                String msg = previsaoFluxoCaixaService.montarRelatorioDisponibilidadeWhatsapp(usuario.getId());
                whatsAppNotificationService.enviarParaUsuario(usuario.getId(), msg);
            } catch (Exception e) {
                log.warn("[SENTINELA] user {}: {}", usuario.getId(), e.getMessage());
            }
        }
    }

    @Scheduled(cron = "0 0 18 ? * SUN", zone = "America/Sao_Paulo")
    @Transactional(readOnly = true)
    public void enviarResumoSemanal() {
        LocalDate hoje = LocalDate.now();
        LocalDate inicioSemana = hoje.with(DayOfWeek.MONDAY);
        LocalDateTime iniAtual = inicioSemana.atStartOfDay();
        LocalDateTime fimAtual = hoje.atTime(23, 59, 59);
        LocalDateTime iniAnterior = inicioSemana.minusWeeks(1).atStartOfDay();
        LocalDateTime fimAnterior = inicioSemana.minusDays(1).atTime(23, 59, 59);

        for (Usuario usuario : usuarioRepository.findAll()) {
            if (usuario.getWhatsappNumero() == null || usuario.getWhatsappNumero().isBlank()) {
                continue;
            }
            BigDecimal atual = nz(transacaoRepository.sumConfirmadaByUsuarioIdAndTipoAndPeriodo(
                usuario.getId(), Transacao.TipoTransacao.DESPESA, iniAtual, fimAtual));
            BigDecimal anterior = nz(transacaoRepository.sumConfirmadaByUsuarioIdAndTipoAndPeriodo(
                usuario.getId(), Transacao.TipoTransacao.DESPESA, iniAnterior, fimAnterior));
            List<Transacao> semana = transacaoRepository.findByUsuarioIdAndTipoAndPeriodo(
                usuario.getId(), Transacao.TipoTransacao.DESPESA, iniAtual, fimAtual);
            Transacao maior = semana.stream().max(Comparator.comparing(Transacao::getValor)).orElse(null);
            List<OrcamentoDTO> orcamentos = orcamentoService.listar(
                usuario.getId(), YearMonth.now().getMonthValue(), YearMonth.now().getYear());
            List<String> limites = orcamentos.stream()
                .filter(o -> o.getPercentualUso() != null && o.getPercentualUso().compareTo(BigDecimal.valueOf(70)) >= 0)
                .map(o -> o.getCategoriaNome() + " (" + o.getPercentualUso() + "%)")
                .collect(Collectors.toList());
            String resumoBase = "Semana atual: " + atual + "\nSemana anterior: " + anterior
                + "\nMaior gasto: " + (maior != null ? maior.getDescricao() + " " + maior.getValor() : "sem despesas")
                + "\nOrçamentos no limite: " + limites;
            String dica = forecastFinanceiroService.gerarDicaSemanal(usuario.getId(), resumoBase);
            String vocativo = jarvisProtocolService.resolveVocative(usuario.getId(), usuarioRepository);
            String linhaMaior = maior != null ? maior.getDescricao() + " — " + BRL.format(maior.getValor()) : "sem despesas confirmadas";
            String linhaOrc = limites.isEmpty() ? "nenhum em patamar crítico" : String.join(", ", limites);
            String msg = jarvisProtocolService.proativoResumoSemanal(
                vocativo,
                BRL.format(atual),
                BRL.format(anterior),
                linhaMaior,
                linhaOrc,
                dica,
                feedbackFamiliar(usuario.getId()));
            whatsAppNotificationService.enviarParaUsuario(usuario.getId(), msg);
        }
    }

    private String feedbackFamiliar(Long usuarioId) {
        if (grupoFamiliarService.grupoAceitoDoUsuario(usuarioId).isEmpty()) {
            return "";
        }
        List<OrcamentoDTO> compartilhados = orcamentoService.listarCompartilhados(
            usuarioId, YearMonth.now().getMonthValue(), YearMonth.now().getYear());
        if (compartilhados.isEmpty()) {
            return "";
        }
        String resumo = compartilhados.stream()
            .map(o -> o.getCategoriaNome() + ": " + o.getPercentualUso() + "% usado")
            .collect(Collectors.joining("; "));
        try {
            String texto = openAiService.gerarTexto(usuarioId,
                "Você é o J.A.R.V.I.S., mediador financeiro familiar discreto. Use apenas os agregados, sem culpar ninguém, em 2 frases curtas; pode mencionar protocolos ou sistemas quando couber.",
                "Orçamentos compartilhados: " + resumo,
                "Destaque categorias compartilhadas próximas do limite, em tom cordial.");
            return "\n\n*Mediação familiar — J.A.R.V.I.S.:* " + texto;
        } catch (Exception e) {
            return "\n\n*Mediação familiar — J.A.R.V.I.S.:* "
                + "Vocês consolidam visibilidade conjunta dos gastos; vale focar nas categorias compartilhadas mais próximas do limite.";
        }
    }

    @Scheduled(cron = "0 30 18 1 * *", zone = "America/Sao_Paulo")
    @Transactional(readOnly = true)
    public void enviarRelatorioMensalScore() {
        YearMonth mesAnterior = YearMonth.now().minusMonths(1);
        for (Usuario usuario : usuarioRepository.findAll()) {
            if (usuario.getWhatsappNumero() == null || usuario.getWhatsappNumero().isBlank()) {
                continue;
            }
            List<OrcamentoDTO> orcamentos = orcamentoService.listar(usuario.getId(), mesAnterior.getMonthValue(), mesAnterior.getYear());
            if (!orcamentos.isEmpty() && orcamentos.stream().allMatch(o -> "VERDE".equalsIgnoreCase(o.getStatus()))) {
                scoreService.registrarEvento(usuario.getId(), ScoreService.EventoScore.ORCAMENTO_NO_VERDE,
                    "Fechou o mês dentro de todos os orçamentos");
            }
            whatsAppNotificationService.enviarParaUsuario(usuario.getId(), scoreService.relatorioMensalEconomia(usuario.getId()));
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
