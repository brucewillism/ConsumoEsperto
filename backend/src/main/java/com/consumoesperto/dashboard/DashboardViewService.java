package com.consumoesperto.dashboard;

import com.consumoesperto.autonomy.LiveInvoiceProjectionService;
import com.consumoesperto.autonomy.SafeToSpendService;
import com.consumoesperto.dto.AgendamentoPagamentoDTO;
import com.consumoesperto.dto.AssinaturaRecorrenteDTO;
import com.consumoesperto.dto.DespesaFixaDTO;
import com.consumoesperto.dto.OrcamentoDTO;
import com.consumoesperto.dto.PrevisaoFuturoChartDTO;
import com.consumoesperto.dto.UsuarioScoreDTO;
import com.consumoesperto.model.MetaFinanceira;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.MetaFinanceiraRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.service.AgendamentoPagamentoService;
import com.consumoesperto.service.AssinaturaRecorrenteService;
import com.consumoesperto.service.ComposicaoProjecaoMesService;
import com.consumoesperto.service.DespesaFixaService;
import com.consumoesperto.service.OrcamentoService;
import com.consumoesperto.service.PrevisaoFluxoCaixaService;
import com.consumoesperto.service.SaldoService;
import com.consumoesperto.service.ScoreService;
import com.consumoesperto.service.TransacaoService;
import com.consumoesperto.util.AppTimeZone;
import com.consumoesperto.util.MoedaUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.consumoesperto.dashboard.DashboardViewDTO.cardItem;
import static com.consumoesperto.dashboard.DashboardViewDTO.line;
import static com.consumoesperto.dashboard.DashboardViewDTO.mapOf;
import static com.consumoesperto.dashboard.DashboardViewDTO.nz;

@Service
@RequiredArgsConstructor
public class DashboardViewService {

    private static final BigDecimal ORCAMENTO_ALERTA_PCT = new BigDecimal("80");
    private static final BigDecimal SAFE_TO_SPEND_BAIXO = new BigDecimal("200");
    private static final BigDecimal ESCUDO_BAIXO_MESES = new BigDecimal("3");

    private final TransacaoService transacaoService;
    private final TransacaoRepository transacaoRepository;
    private final SaldoService saldoService;
    private final ComposicaoProjecaoMesService composicaoProjecaoMesService;
    private final LiveInvoiceProjectionService liveInvoiceProjectionService;
    private final FaturaRepository faturaRepository;
    private final SafeToSpendService safeToSpendService;
    private final DespesaFixaService despesaFixaService;
    private final AssinaturaRecorrenteService assinaturaRecorrenteService;
    private final AgendamentoPagamentoService agendamentoPagamentoService;
    private final OrcamentoService orcamentoService;
    private final ScoreService scoreService;
    private final MetaFinanceiraRepository metaFinanceiraRepository;
    private final PrevisaoFluxoCaixaService previsaoFluxoCaixaService;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public DashboardViewDTO montar(Long usuarioId, DashboardViewMode mode) {
        DashboardViewMode resolved = mode == null ? DashboardViewMode.GENERAL : mode;
        return resolved == DashboardViewMode.MONTHLY ? monthly(usuarioId) : general(usuarioId);
    }

    @Transactional
    public DashboardViewMode persistirPreferencia(Long usuarioId, DashboardViewMode mode) {
        DashboardViewMode resolved = mode == null ? DashboardViewMode.MONTHLY : mode;
        usuarioRepository.findById(usuarioId).ifPresent(u -> {
            u.setUltimaVisaoDashboard(resolved.name());
            usuarioRepository.save(u);
        });
        return resolved;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> lerPreferencia(Long usuarioId) {
        String saved = usuarioRepository.findById(usuarioId)
            .map(u -> u.getUltimaVisaoDashboard())
            .orElse(null);
        boolean persistida = saved != null && !saved.isBlank();
        DashboardViewMode mode = persistida ? DashboardViewMode.from(saved) : DashboardViewMode.MONTHLY;
        return Map.of("viewMode", mode.name(), "persistida", persistida);
    }

    private DashboardViewDTO monthly(Long usuarioId) {
        YearMonth ym = AppTimeZone.mesAtual();
        LocalDate hoje = AppTimeZone.hoje();
        LocalDateTime inicio = ym.atDay(1).atStartOfDay();
        LocalDateTime fim = ym.atEndOfMonth().atTime(23, 59, 59);

        Map<String, Object> resumo = transacaoService.resumoFinanceiroMes(usuarioId, ym, true);
        SaldoService.ProjecaoMesCaixa projecao = saldoService.calcularProjecaoMes(usuarioId);
        ComposicaoProjecaoMesService.PartesObrigacoesMes partes =
            composicaoProjecaoMesService.partesObrigacoesMes(usuarioId, ym, hoje);

        List<Map<String, Object>> faturasMes = faturasDoMes(usuarioId, ym);
        BigDecimal faturaMesTotal = faturasMes.stream()
            .map(m -> nz((BigDecimal) m.get("valorProjetado")))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        if (faturaMesTotal.compareTo(BigDecimal.ZERO) == 0) {
            faturaMesTotal = partes.faturas();
        }

        List<Transacao> parcelasMes = transacaoRepository.findParcelasEmprestimoNoMes(usuarioId, inicio, fim);
        List<Map<String, Object>> parcelasRows = parcelasMes.stream().map(this::parcelaMensalRow).toList();
        BigDecimal parcelaMesTotal = parcelasRows.stream()
            .map(m -> nz((BigDecimal) m.get("valor")))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        long parcelasFolhaQtd = parcelasMes.stream().filter(t -> Boolean.TRUE.equals(t.getDescontoEmFolha())).count();

        List<Map<String, Object>> fixas = despesaFixaService.listar(usuarioId).stream()
            .map(this::fixaRow)
            .collect(Collectors.toList());
        List<Map<String, Object>> assinaturas = assinaturaRecorrenteService.listar(usuarioId).stream()
            .filter(AssinaturaRecorrenteDTO::isAtivo)
            .map(this::assinaturaRow)
            .collect(Collectors.toList());
        List<Map<String, Object>> agendamentos = agendamentoPagamentoService.listar(usuarioId).stream()
            .filter(a -> agendamentoNoMesForaCartao(a, ym))
            .map(this::agendamentoRow)
            .collect(Collectors.toList());

        List<OrcamentoDTO> orcamentos = orcamentoService.listar(usuarioId, ym.getMonthValue(), ym.getYear());
        Map<String, Object> safe = safeOrEmpty(usuarioId);
        BigDecimal safeToSpend = nz(asBd(safe.get("safeToSpend")));

        BigDecimal receitas = nz(asBd(resumo.get("totalReceitas")));
        BigDecimal despesas = nz(asBd(resumo.get("totalDespesas")));
        BigDecimal resultado = receitas.subtract(despesas).setScale(2, RoundingMode.HALF_UP);
        BigDecimal projecaoMes = nz(projecao.saldoProjetadoFimMes());

        BigDecimal comprometido = partes.comprometidoDisjuntoExibicao();
        BigDecimal somaBlocos = partes.fixas().add(partes.faturas()).add(partes.parcelasEmprestimoTodas())
            .setScale(2, RoundingMode.HALF_UP);

        List<Map<String, Object>> itens = new ArrayList<>();
        itens.add(cardItem("receitasMes", "Receitas do mês", receitas, "Apenas lançamentos confirmados", "positivo"));
        itens.add(cardItem("despesasMes", "Despesas do mês", despesas, "Apenas lançamentos confirmados", "negativo"));
        itens.add(cardItem("resultadoMes", "Resultado do mês", resultado, "Receitas − despesas confirmadas",
            resultado.signum() >= 0 ? "positivo" : "negativo"));
        itens.add(cardItem("projecaoMes", "Projeção do mês", projecaoMes,
            "Saldo projetado até o fim do mês (inclui previstos)", "neutro"));
        itens.add(cardItem("faturaMes", "Fatura do mês", faturaMesTotal,
            "Já inclui parcelas de compras no cartão", "negativo"));
        itens.add(cardItem("parcelaMes", "Parcela do mês", parcelaMesTotal,
            parcelasFolhaQtd > 0
                ? parcelasFolhaQtd + " consignado(s) com desconto em folha (não debitam a conta)"
                : "Empréstimos/financiamentos deste mês — sem o total da dívida",
            "negativo"));
        itens.add(cardItem("safeToSpendMes", "Safe-to-spend do mês", safeToSpend,
            "Disponível após obrigações do mês (consignado em folha não sai da conta)", "positivo"));

        Map<String, Object> cards = mapOf("itens", itens);
        Map<String, Object> metricas = mapOf(
            "receitasConfirmadas", receitas,
            "despesasConfirmadas", despesas,
            "resultadoMes", resultado,
            "projecaoMes", projecaoMes,
            "safeToSpendMes", safeToSpend,
            "diasRestantesNoMes", safe.getOrDefault("diasRestantesNoMes", projecao.diasNoMes() - projecao.diaAtual()),
            "comprometidoMes", comprometido,
            "somaBlocos", somaBlocos,
            "blocosBatemComTotal", somaBlocos.compareTo(comprometido) == 0,
            "faturaMesExibida", faturaMesTotal,
            "parcelaMesExibida", parcelaMesTotal
        );

        Map<String, Object> compromissos = mapOf(
            "cartaoFatura", mapOf(
                "rotulo", "Fatura do mês",
                "total", faturaMesTotal,
                "itens", faturasMes
            ),
            "emprestimos", mapOf(
                "rotulo", "Parcela do mês",
                "total", parcelaMesTotal,
                "quantidade", parcelasRows.size(),
                "quantidadeDescontoEmFolha", parcelasFolhaQtd,
                "itens", parcelasRows
            ),
            "fixasAssinaturasAgendamentos", mapOf(
                "rotulo", "Fixas, assinaturas e agendamentos",
                "total", partes.fixas(),
                "fixas", fixas,
                "assinaturas", assinaturas,
                "agendamentos", agendamentos
            ),
            "orcamentos", orcamentos.stream().map(this::orcamentoRow).toList(),
            "metas", metasDoMes(usuarioId)
        );

        List<Object[]> categorias = transacaoRepository.findDespesasByUsuarioIdAndPeriodoGroupByCategoria(
            usuarioId, inicio, fim);
        List<Map<String, String>> insights = insightsMensal(
            categorias, despesas, orcamentos, faturaMesTotal, parcelaMesTotal, safeToSpend, projecaoMes);
        List<Map<String, String>> alertas = alertasMensal(orcamentos, faturaMesTotal, safeToSpend, categorias);

        return DashboardViewDTO.builder()
            .viewMode(DashboardViewMode.MONTHLY.name())
            .periodo(ym.toString())
            .cards(cards)
            .metricas(metricas)
            .compromissos(compromissos)
            .insights(insights)
            .alertas(alertas)
            .build();
    }

    private DashboardViewDTO general(Long usuarioId) {
        YearMonth ym = AppTimeZone.mesAtual();
        BigDecimal liquido = nz(saldoService.patrimonioLiquido(usuarioId));
        BigDecimal passivoEmprestimo = nz(transacaoRepository.sumPassivoEmprestimoAtivo(usuarioId));
        BigDecimal ativos = liquido.add(passivoEmprestimo).setScale(2, RoundingMode.HALF_UP);
        BigDecimal exposicaoCartao = nz(faturaRepository.sumValorFaturasPendentesByUsuarioId(usuarioId));
        BigDecimal dividaTotal = passivoEmprestimo.add(exposicaoCartao).setScale(2, RoundingMode.HALF_UP);
        BigDecimal reservas = nz(saldoService.saldoLiquidezImediata(usuarioId));
        BigDecimal folgaPatrimonial = reservas;

        List<Map<String, Object>> emprestimos = detalharEmprestimos(usuarioId);
        Map<String, BigDecimal> passivoPorTipo = new LinkedHashMap<>();
        for (Map<String, Object> e : emprestimos) {
            String tipo = String.valueOf(e.getOrDefault("tipo", "OUTRO"));
            passivoPorTipo.merge(tipo, nz((BigDecimal) e.get("saldoDevedor")), BigDecimal::add);
        }

        UsuarioScoreDTO score = scoreService.obter(usuarioId);
        BigDecimal mesesEscudo = mesesEscudo(usuarioId);
        List<Map<String, Object>> faturasAbertas = liveInvoiceProjectionService.atuais(usuarioId);
        List<Map<String, Object>> metas = metasGerais(usuarioId);

        BigDecimal totalEmContas = ativos;
        BigDecimal totalInvestido = ativos.subtract(reservas).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        List<Map<String, Object>> itens = new ArrayList<>();
        itens.add(cardItem("ativos", "Ativos (contas + investimentos)", ativos,
            "Saldos das contas ativas — mesma base do patrimônio líquido", "positivo"));
        itens.add(cardItem("totalEmContas", "Total em contas", totalEmContas,
            "Mesmo conjunto de ativos; não soma fatura nem empréstimo", "positivo"));
        itens.add(cardItem("totalInvestido", "Total investido", totalInvestido,
            "Ativos − reservas (liquidez). Sem tipo de conta INVESTIMENTO no catálogo, tende a zero", "positivo"));
        itens.add(cardItem("passivos", "Passivos (saldo devedor)", passivoEmprestimo,
            "Empréstimos e financiamentos — não inclui fatura de cartão", "negativo"));
        itens.add(cardItem("patrimonioLiquido", "Patrimônio líquido", liquido,
            "Ativos − passivos de empréstimo", liquido.signum() >= 0 ? "positivo" : "negativo"));
        itens.add(cardItem("dividaTotal", "Dívida total", dividaTotal,
            "Saldo devedor de empréstimos + exposição de cartão (sem interseção)", "negativo"));
        itens.add(cardItem("reservas", "Reservas (liquidez imediata)", reservas, "Corrente, poupança e dinheiro", "positivo"));
        itens.add(cardItem("folgaPatrimonial", "Folga patrimonial", folgaPatrimonial,
            "Não é safe-to-spend — liquidez imediata, semântica patrimonial", "neutro"));
        itens.add(cardItem("score", "Score", score.getScore() == null ? BigDecimal.ZERO : BigDecimal.valueOf(score.getScore()),
            score.getNivel() == null ? "" : "Nível " + score.getNivel(), "neutro"));

        Map<String, Object> cards = mapOf("itens", itens);
        Map<String, Object> metricas = mapOf(
            "ativos", ativos,
            "totalEmContas", totalEmContas,
            "totalInvestido", totalInvestido,
            "passivos", passivoEmprestimo,
            "passivosPorTipo", passivoPorTipo,
            "patrimonioLiquido", liquido,
            "ativosMenosPassivos", ativos.subtract(passivoEmprestimo).setScale(2, RoundingMode.HALF_UP),
            "identidadePatrimonialOk", ativos.subtract(passivoEmprestimo).compareTo(liquido) == 0,
            "exposicaoCartao", exposicaoCartao,
            "dividaTotal", dividaTotal,
            "reservas", reservas,
            "folgaPatrimonial", folgaPatrimonial,
            "score", score.getScore(),
            "scoreNivel", score.getNivel(),
            "mesesEscudoEnergia", mesesEscudo
        );
        Map<String, Object> compromissos = mapOf(
            "emprestimos", mapOf(
                "rotulo", "Saldo devedor por contrato",
                "total", passivoEmprestimo,
                "itens", emprestimos
            ),
            "cartaoFatura", mapOf(
                "rotulo", "Exposição de cartão",
                "total", exposicaoCartao,
                "itens", faturasAbertas
            ),
            "metas", metas
        );

        List<Map<String, String>> insights = insightsGeral(liquido, dividaTotal, metas, score, mesesEscudo);
        List<Map<String, String>> alertas = alertasGeral(liquido, dividaTotal, reservas, score, mesesEscudo);

        return DashboardViewDTO.builder()
            .viewMode(DashboardViewMode.GENERAL.name())
            .periodo(ym.toString())
            .cards(cards)
            .metricas(metricas)
            .compromissos(compromissos)
            .insights(insights)
            .alertas(alertas)
            .build();
    }

    private List<Map<String, Object>> faturasDoMes(Long usuarioId, YearMonth ym) {
        List<Map<String, Object>> atuais = liveInvoiceProjectionService.atuais(usuarioId);
        List<Map<String, Object>> doMes = new ArrayList<>();
        for (Map<String, Object> f : atuais) {
            if (faturaNoMes(f, ym)) {
                doMes.add(f);
            }
        }
        return doMes;
    }

    private static boolean faturaNoMes(Map<String, Object> f, YearMonth ym) {
        Object venc = f.get("dataVencimento");
        Object fecha = f.get("dataFechamento");
        LocalDate d = toLocalDate(fecha);
        if (d == null) {
            d = toLocalDate(venc);
        }
        return d != null && YearMonth.from(d).equals(ym);
    }

    private static LocalDate toLocalDate(Object raw) {
        if (raw instanceof LocalDate ld) {
            return ld;
        }
        if (raw instanceof LocalDateTime ldt) {
            return ldt.toLocalDate();
        }
        if (raw instanceof java.util.Date d) {
            return new java.sql.Date(d.getTime()).toLocalDate();
        }
        if (raw instanceof String s && !s.isBlank()) {
            try {
                if (s.length() >= 10) {
                    return LocalDate.parse(s.substring(0, 10));
                }
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private Map<String, Object> parcelaMensalRow(Transacao t) {
        boolean folha = Boolean.TRUE.equals(t.getDescontoEmFolha());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("emprestimoId", t.getEmprestimoId());
        m.put("descricao", t.getDescricao());
        m.put("valor", MoedaUtil.nz(t.getValor()));
        m.put("descontoEmFolha", folha);
        m.put("indicacao", folha ? "Desconto em folha — não debita a conta" : "Debita a conta");
        m.put("parcelaAtual", t.getParcelaAtual());
        m.put("totalParcelas", t.getTotalParcelas());
        m.put("data", t.getDataTransacao());
        return m;
    }

    private List<Map<String, Object>> detalharEmprestimos(Long usuarioId) {
        List<String> ids = transacaoRepository.findEmprestimoIdsByUsuario(usuarioId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (String id : ids) {
            List<Transacao> txs = transacaoRepository.findByUsuarioIdAndEmprestimoIdOrderByDataTransacaoAsc(usuarioId, id);
            List<Transacao> despesas = txs.stream()
                .filter(t -> t.getTipoTransacao() == Transacao.TipoTransacao.DESPESA)
                .toList();
            List<Transacao> restantes = despesas.stream()
                .filter(t -> t.getStatusConferencia() == Transacao.StatusConferencia.PREVISTO)
                .toList();
            if (restantes.isEmpty() && despesas.stream().noneMatch(t -> !t.isExcluido())) {
                continue;
            }
            BigDecimal saldoDevedor = restantes.stream()
                .map(t -> MoedaUtil.nz(t.getValor()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalContratado = txs.stream()
                .filter(t -> t.getTipoTransacao() == Transacao.TipoTransacao.RECEITA)
                .map(t -> MoedaUtil.nz(t.getValor()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalContratado.signum() == 0) {
                totalContratado = despesas.stream()
                    .map(t -> MoedaUtil.nz(t.getValor()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            Transacao amostra = restantes.isEmpty() ? (despesas.isEmpty() ? null : despesas.get(0)) : restantes.get(0);
            boolean folha = restantes.stream().anyMatch(t -> Boolean.TRUE.equals(t.getDescontoEmFolha()))
                || (amostra != null && Boolean.TRUE.equals(amostra.getDescontoEmFolha()));
            String tipo = classificarTipoEmprestimo(amostra, folha);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("emprestimoId", id);
            row.put("descricao", amostra != null ? amostra.getDescricao() : "Empréstimo");
            row.put("tipo", tipo);
            row.put("descontoEmFolha", folha);
            row.put("saldoDevedor", saldoDevedor);
            row.put("totalContratado", totalContratado.setScale(2, RoundingMode.HALF_UP));
            row.put("parcelasRestantes", restantes.size());
            row.put("valorParcela", amostra != null ? MoedaUtil.nz(amostra.getValor()) : BigDecimal.ZERO);
            row.put("totalParcelas", amostra != null ? amostra.getTotalParcelas() : restantes.size());
            out.add(row);
        }
        return out;
    }

    private static String classificarTipoEmprestimo(Transacao amostra, boolean folha) {
        String d = amostra != null && amostra.getDescricao() != null ? amostra.getDescricao().toLowerCase() : "";
        if (d.contains("financi")) {
            return "FINANCIAMENTO";
        }
        if (folha || d.contains("consign")) {
            return "CONSIGNADO";
        }
        return "PESSOAL";
    }

    private Map<String, Object> fixaRow(DespesaFixaDTO d) {
        return mapOf(
            "id", d.getId(),
            "descricao", d.getDescricao(),
            "valor", d.getValor(),
            "diaVencimento", d.getDiaVencimento(),
            "debitoAutomatico", d.getDebitoAutomatico()
        );
    }

    private Map<String, Object> assinaturaRow(AssinaturaRecorrenteDTO a) {
        return mapOf(
            "id", a.getId(),
            "nome", a.getNome(),
            "valor", a.getValor(),
            "diaVencimento", a.getDiaVencimento()
        );
    }

    private Map<String, Object> agendamentoRow(AgendamentoPagamentoDTO a) {
        return mapOf(
            "id", a.getId(),
            "beneficiario", a.getBeneficiario(),
            "valor", a.getValor(),
            "dataVencimento", a.getDataVencimento(),
            "proximaExecucao", a.getProximaExecucao(),
            "status", a.getStatus()
        );
    }

    private static boolean agendamentoNoMesForaCartao(AgendamentoPagamentoDTO a, YearMonth ym) {
        if (a.getCartaoCreditoId() != null) {
            return false;
        }
        LocalDate ref = a.getProximaExecucao() != null ? a.getProximaExecucao() : a.getDataVencimento();
        return ref != null && YearMonth.from(ref).equals(ym);
    }

    private Map<String, Object> orcamentoRow(OrcamentoDTO o) {
        return mapOf(
            "categoria", o.getCategoriaNome(),
            "limite", o.getValorLimite(),
            "gasto", o.getValorGasto(),
            "percentualUso", o.getPercentualUso(),
            "status", o.getStatus()
        );
    }

    private List<Map<String, Object>> metasDoMes(Long usuarioId) {
        return metaFinanceiraRepository.findByUsuarioIdOrderByPrioridadeDescDataCriacaoDesc(usuarioId)
            .stream()
            .filter(this::metaVigente)
            .limit(6)
            .map(m -> mapOf(
                "descricao", m.getDescricao(),
                "valorPoupadoMensal", m.getValorPoupadoMensal(),
                "valorTotal", m.getValorTotal(),
                "valorAcumulado", m.getValorAcumulado()
            ))
            .toList();
    }

    private List<Map<String, Object>> metasGerais(Long usuarioId) {
        return metaFinanceiraRepository.findByUsuarioIdOrderByPrioridadeDescDataCriacaoDesc(usuarioId)
            .stream()
            .filter(this::metaVigente)
            .map(m -> {
                BigDecimal total = nz(m.getValorTotal());
                BigDecimal acum = nz(m.getValorAcumulado());
                BigDecimal pct = total.signum() > 0
                    ? acum.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
                return mapOf(
                    "descricao", m.getDescricao(),
                    "valorTotal", total,
                    "valorAcumulado", acum,
                    "progressoPct", pct,
                    "prazoMeses", m.getPrazoMeses()
                );
            })
            .toList();
    }

    private boolean metaVigente(MetaFinanceira m) {
        if (m.getDataExpiracao() == null) {
            return true;
        }
        return !m.getDataExpiracao().isBefore(AppTimeZone.hoje());
    }

    private Map<String, Object> safeOrEmpty(Long usuarioId) {
        try {
            return safeToSpendService.calcular(usuarioId);
        } catch (Exception e) {
            return Map.of("safeToSpend", BigDecimal.ZERO);
        }
    }

    private BigDecimal mesesEscudo(Long usuarioId) {
        try {
            PrevisaoFuturoChartDTO chart = previsaoFluxoCaixaService.buildPrevisaoFuturoChart(usuarioId);
            return chart.getMesesEscudoEnergia();
        } catch (Exception e) {
            return null;
        }
    }

    private List<Map<String, String>> insightsMensal(
        List<Object[]> categorias,
        BigDecimal despesas,
        List<OrcamentoDTO> orcamentos,
        BigDecimal faturaMes,
        BigDecimal parcelaMes,
        BigDecimal safeToSpend,
        BigDecimal projecaoMes
    ) {
        List<Map<String, String>> out = new ArrayList<>();
        if (categorias != null && !categorias.isEmpty() && categorias.get(0)[0] != null) {
            Object[] top = categorias.get(0);
            out.add(line("CATEGORIA_PESO", "Categoria que mais pesou no mês",
                top[0] + " — " + MoedaUtil.nz(asBd(top[1]))));
        }
        orcamentos.stream()
            .filter(o -> o.getPercentualUso() != null && o.getPercentualUso().compareTo(ORCAMENTO_ALERTA_PCT) >= 0)
            .findFirst()
            .ifPresent(o -> out.add(line("ORCAMENTO_ESTOURANDO", "Orçamento a estourar",
                o.getCategoriaNome() + " em " + o.getPercentualUso() + "% do limite")));
        if (faturaMes.compareTo(despesas.multiply(new BigDecimal("0.4"))) > 0 && faturaMes.signum() > 0) {
            out.add(line("FATURA_ALTA", "Fatura do mês relevante",
                "A fatura do mês (" + faturaMes + ") concentra boa parte das saídas."));
        }
        if (parcelaMes.signum() > 0) {
            out.add(line("PARCELA_RELEVANTE", "Parcela do mês",
                "Empréstimos pesam " + parcelaMes + " neste mês (sem o saldo devedor)."));
        }
        out.add(line("PROJECAO_FIM_MES", "Saldo projetado até o fim do mês", String.valueOf(projecaoMes)));
        if (safeToSpend.compareTo(SAFE_TO_SPEND_BAIXO) <= 0) {
            out.add(line("SAFE_TO_SPEND_BAIXO", "Safe-to-spend do mês baixo", String.valueOf(safeToSpend)));
        }
        return out;
    }

    private List<Map<String, String>> alertasMensal(
        List<OrcamentoDTO> orcamentos,
        BigDecimal faturaMes,
        BigDecimal safeToSpend,
        List<Object[]> categorias
    ) {
        List<Map<String, String>> out = new ArrayList<>();
        for (OrcamentoDTO o : orcamentos) {
            if (o.getPercentualUso() != null && o.getPercentualUso().compareTo(new BigDecimal("100")) >= 0) {
                out.add(line("ORCAMENTO_ESTOURANDO", "Orçamento estourando",
                    o.getCategoriaNome() + " ultrapassou o limite"));
            }
        }
        if (faturaMes.compareTo(new BigDecimal("1500")) >= 0) {
            out.add(line("FATURA_ALTA", "Fatura alta", "Fatura do mês: " + faturaMes));
        }
        if (safeToSpend.compareTo(SAFE_TO_SPEND_BAIXO) <= 0) {
            out.add(line("SAFE_TO_SPEND_BAIXO", "Safe-to-spend do mês baixo", String.valueOf(safeToSpend)));
        }
        if (categorias != null && categorias.size() == 1 && asBd(categorias.get(0)[1]).compareTo(new BigDecimal("500")) > 0) {
            out.add(line("CONCENTRACAO", "Concentração de gastos",
                "Quase tudo do mês está em " + categorias.get(0)[0]));
        }
        return out;
    }

    private List<Map<String, String>> insightsGeral(
        BigDecimal liquido,
        BigDecimal dividaTotal,
        List<Map<String, Object>> metas,
        UsuarioScoreDTO score,
        BigDecimal mesesEscudo
    ) {
        List<Map<String, String>> out = new ArrayList<>();
        out.add(line("EVOLUCAO_PATRIMONIAL", "Patrimônio líquido",
            "Ativos − passivos = " + liquido + (liquido.signum() < 0
                ? " (negativo porque o saldo devedor supera as contas)" : "")));
        out.add(line("DIVIDA_TOTAL", "Dívida total",
            "Empréstimos + exposição de cartão = " + dividaTotal + " (sem contar a mesma obrigação duas vezes)"));
        if (!metas.isEmpty()) {
            Object pct = metas.get(0).get("progressoPct");
            out.add(line("PROGRESSO_METAS", "Progresso de metas",
                metas.get(0).get("descricao") + " em " + pct + "%"));
        }
        if (score.getScore() != null) {
            out.add(line("TENDENCIA_SCORE", "Score", score.getScore() + " (" + score.getNivel() + ")"));
        }
        if (mesesEscudo != null) {
            out.add(line("ESCUDO", "Escudo de Energia", mesesEscudo + " meses de reserva estimada"));
        }
        return out;
    }

    private List<Map<String, String>> alertasGeral(
        BigDecimal liquido,
        BigDecimal dividaTotal,
        BigDecimal reservas,
        UsuarioScoreDTO score,
        BigDecimal mesesEscudo
    ) {
        List<Map<String, String>> out = new ArrayList<>();
        if (liquido.signum() < 0) {
            out.add(line("PATRIMONIO_NEGATIVO", "Patrimônio líquido negativo",
                "Os passivos de empréstimo superam os ativos em contas."));
        }
        if (dividaTotal.compareTo(ativosSafe(liquido, dividaTotal)) > 0 && dividaTotal.signum() > 0) {
            out.add(line("DIVIDA_ALTA", "Dívida total elevada", String.valueOf(dividaTotal)));
        }
        if (reservas.compareTo(new BigDecimal("500")) < 0) {
            out.add(line("RESERVA_BAIXA", "Reserva baixa", "Liquidez imediata: " + reservas));
        }
        if (score.getScore() != null && score.getScore() < 550) {
            out.add(line("SCORE_PIORANDO", "Score em zona de atenção", String.valueOf(score.getScore())));
        }
        if (mesesEscudo != null && mesesEscudo.compareTo(ESCUDO_BAIXO_MESES) < 0) {
            out.add(line("ESCUDO_BAIXO", "Escudo de Energia baixo", mesesEscudo + " meses"));
        }
        return out;
    }

    private static BigDecimal ativosSafe(BigDecimal liquido, BigDecimal divida) {
        return nz(liquido).abs().add(nz(divida));
    }

    private static BigDecimal asBd(Object v) {
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }
}
