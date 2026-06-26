package com.consumoesperto.service;

import com.consumoesperto.config.ForecastProjecaoConfig;
import com.consumoesperto.dto.ProjecaoMesResumoDTO;
import com.consumoesperto.dto.RendaConfigDTO;
import com.consumoesperto.dto.SerieProjecaoSafraDTO;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.Fatura;
import com.consumoesperto.model.TipoConfiguracaoRenda;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.ContaBancariaRepository;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.TransacaoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Hub de patrimônio e projeção de caixa.
 * Multicarteira: soma {@link com.consumoesperto.model.ContaBancaria#getSaldoAtual()} (movimentado por {@link SaldoMovimentacaoService}).
 * Legado: receitas − despesas − investimentos confirmados (lifetime).
 */
@Service
@Slf4j
public class SaldoService {

    private final TransacaoRepository transacaoRepository;
    private final FaturaRepository faturaRepository;
    private final ContaBancariaRepository contaBancariaRepository;
    private final OpenAiService openAiService;
    private final ContaBancariaService contaBancariaService;
    private final SaldoMovimentacaoService saldoMovimentacaoService;
    private final RendaConfigService rendaConfigService;
    private final PlanejamentoFiscalService planejamentoFiscalService;
    private final ConciliacaoAuditoriaService conciliacaoAuditoriaService;
    private final DespesaFixaService despesaFixaService;
    private final ForecastProjecaoConfig forecastProjecaoConfig;

    public SaldoService(
        TransacaoRepository transacaoRepository,
        FaturaRepository faturaRepository,
        ContaBancariaRepository contaBancariaRepository,
        OpenAiService openAiService,
        ContaBancariaService contaBancariaService,
        SaldoMovimentacaoService saldoMovimentacaoService,
        RendaConfigService rendaConfigService,
        @Lazy PlanejamentoFiscalService planejamentoFiscalService,
        @Lazy ConciliacaoAuditoriaService conciliacaoAuditoriaService,
        DespesaFixaService despesaFixaService,
        ForecastProjecaoConfig forecastProjecaoConfig
    ) {
        this.transacaoRepository = transacaoRepository;
        this.faturaRepository = faturaRepository;
        this.contaBancariaRepository = contaBancariaRepository;
        this.openAiService = openAiService;
        this.contaBancariaService = contaBancariaService;
        this.saldoMovimentacaoService = saldoMovimentacaoService;
        this.rendaConfigService = rendaConfigService;
        this.planejamentoFiscalService = planejamentoFiscalService;
        this.conciliacaoAuditoriaService = conciliacaoAuditoriaService;
        this.despesaFixaService = despesaFixaService;
        this.forecastProjecaoConfig = forecastProjecaoConfig;
    }

    public record ResultadoReconciliacaoSaldo(
        Long contaId,
        BigDecimal saldoAnterior,
        BigDecimal saldoCalculado,
        int transacoesConsideradas
    ) {}

    /**
     * Projeção de fechamento do mês corrente — fonte única para Forecast, Sentinela e alertas.
     * {@code receitasPrevistas} = gap salarial mensal; {@code receitasFiscaisPrevistas} = 13º/IR ainda PREVISTO.
     */
    public record ProjecaoMesCaixa(
        YearMonth competencia,
        BigDecimal patrimonioLiquido,
        BigDecimal gastoAtual,
        BigDecimal gastoProjetado,
        BigDecimal rendaLiquida,
        BigDecimal receitasPrevistas,
        BigDecimal receitasFiscaisPrevistas,
        BigDecimal despesasPrevistas,
        BigDecimal saldoProjetadoFimMes,
        int diaAtual,
        int diasNoMes
    ) {
        /** Soma de entradas previstas (salário + receitas sazonais fiscais). */
        public BigDecimal receitasPrevistasConsolidadas() {
            return receitasPrevistas.add(receitasFiscaisPrevistas);
        }
    }

    /** Série cascata M, M+1, … — saldo final de cada mês alimenta o patrimônio inicial do seguinte. */
    public record SerieProjecaoSafra(List<ProjecaoMesCaixa> meses) {}

    /**
     * Saldo exibido no dashboard: soma das carteiras ativas ou, sem multicarteira, saldo derivado de transações.
     */
    public BigDecimal saldoContaCorrente(Long usuarioId) {
        return patrimonioLiquido(usuarioId);
    }

    @Transactional(readOnly = true)
    public boolean usaMulticarteira(Long usuarioId) {
        return contaBancariaService.possuiContasAtivas(usuarioId);
    }

    /**
     * Patrimônio líquido em contas (multicarteira) ou saldo derivado de transações (legado).
     */
    @Transactional(readOnly = true)
    public BigDecimal patrimonioLiquido(Long usuarioId) {
        if (usaMulticarteira(usuarioId)) {
            return contaBancariaService.somarSaldosAtivos(usuarioId);
        }
        return saldoConfirmado(usuarioId);
    }

    /**
     * Projeção do mês corrente — delegação para {@link #calcularProjecaoMes(Long, YearMonth, BigDecimal)}.
     */
    @Transactional(readOnly = true)
    public ProjecaoMesCaixa calcularProjecaoMes(Long usuarioId) {
        return calcularProjecaoMes(usuarioId, YearMonth.now(), null);
    }

    /**
     * Projeção de um mês específico. Meses futuros exigem {@code patrimonioInicial} (saldo cascata do mês anterior).
     */
    @Transactional(readOnly = true)
    public ProjecaoMesCaixa calcularProjecaoMes(Long usuarioId, YearMonth ym, BigDecimal patrimonioInicial) {
        YearMonth mesAtual = YearMonth.now();
        if (ym.isBefore(mesAtual)) {
            throw new IllegalArgumentException("Projeção disponível apenas para o mês corrente e meses futuros.");
        }
        if (ym.equals(mesAtual)) {
            return calcularProjecaoMesCorrente(usuarioId, patrimonioInicial);
        }
        ProjecaoMesCaixa ref = calcularProjecaoMesCorrente(usuarioId, null);
        BigDecimal mediaDiaria = ref.gastoAtual().divide(
            BigDecimal.valueOf(Math.max(1, ref.diaAtual())), 2, RoundingMode.HALF_UP);
        BigDecimal patrimonioBase = patrimonioInicial != null ? patrimonioInicial : ref.saldoProjetadoFimMes();
        return calcularProjecaoMesFuturo(usuarioId, ym, patrimonioBase, mediaDiaria);
    }

    /**
     * Safra cumulativa: mês corrente + {@code mesesParaFrente} meses subsequentes (ex.: 2 → M, M+1, M+2).
     */
    @Transactional(readOnly = true)
    public SerieProjecaoSafra calcularProjecaoSafra(Long usuarioId, int mesesParaFrente) {
        int total = Math.max(1, mesesParaFrente + 1);
        List<ProjecaoMesCaixa> meses = new ArrayList<>(total);
        YearMonth ym = YearMonth.now();
        BigDecimal patrimonioCascata = null;

        ProjecaoMesCaixa corrente = calcularProjecaoMesCorrente(usuarioId, null);
        meses.add(corrente);
        patrimonioCascata = corrente.saldoProjetadoFimMes();

        BigDecimal mediaDiaria = corrente.gastoAtual().divide(
            BigDecimal.valueOf(Math.max(1, corrente.diaAtual())), 2, RoundingMode.HALF_UP);

        for (int i = 1; i < total; i++) {
            ym = ym.plusMonths(1);
            ProjecaoMesCaixa proximo = calcularProjecaoMesFuturo(usuarioId, ym, patrimonioCascata, mediaDiaria);
            meses.add(proximo);
            patrimonioCascata = proximo.saldoProjetadoFimMes();
        }
        return new SerieProjecaoSafra(meses);
    }

    @Transactional(readOnly = true)
    public SerieProjecaoSafraDTO calcularProjecaoSafraDto(Long usuarioId, int mesesParaFrente) {
        SerieProjecaoSafra safra = calcularProjecaoSafra(usuarioId, mesesParaFrente);
        SerieProjecaoSafraDTO dto = new SerieProjecaoSafraDTO();
        BigDecimal patrimonioAnterior = null;
        for (ProjecaoMesCaixa p : safra.meses()) {
            ProjecaoMesResumoDTO m = new ProjecaoMesResumoDTO();
            m.setCompetencia(p.competencia().toString());
            m.setRotuloMes(formatarRotuloMes(p.competencia()));
            m.setPatrimonioInicial(patrimonioAnterior != null ? patrimonioAnterior : p.patrimonioLiquido());
            m.setPatrimonioLiquido(p.patrimonioLiquido());
            m.setReceitasPrevistas(p.receitasPrevistas());
            m.setReceitasFiscaisPrevistas(p.receitasFiscaisPrevistas());
            m.setDespesasPrevistas(p.despesasPrevistas());
            m.setSaldoProjetadoFimMes(p.saldoProjetadoFimMes());
            dto.getMeses().add(m);
            patrimonioAnterior = p.saldoProjetadoFimMes();
        }
        return dto;
    }

    private ProjecaoMesCaixa calcularProjecaoMesCorrente(Long usuarioId, BigDecimal patrimonioInicialOverride) {
        YearMonth ym = YearMonth.now();
        LocalDate hoje = LocalDate.now();
        LocalDateTime inicio = ym.atDay(1).atStartOfDay();
        LocalDateTime fimHoje = hoje.atTime(23, 59, 59);
        LocalDateTime fimMes = ym.atEndOfMonth().atTime(23, 59, 59);

        int diaAtual = Math.max(1, hoje.getDayOfMonth());
        int diasNoMes = ym.lengthOfMonth();

        BigDecimal gastoAtual = nz(transacaoRepository.sumConfirmadaByUsuarioIdAndTipoAndPeriodo(
            usuarioId, Transacao.TipoTransacao.DESPESA, inicio, fimHoje));
        BigDecimal mediaDiaria = gastoAtual.divide(BigDecimal.valueOf(diaAtual), 2, RoundingMode.HALF_UP);
        BigDecimal gastoProjetado = mediaDiaria.multiply(BigDecimal.valueOf(diasNoMes)).setScale(2, RoundingMode.HALF_UP);

        BigDecimal rendaLiquida = rendaConfigService.getRendaMensalEstimada(usuarioId);
        if (rendaLiquida.compareTo(BigDecimal.ZERO) <= 0) {
            rendaLiquida = nz(transacaoRepository.sumConfirmadaByUsuarioIdAndTipoAndPeriodo(
                usuarioId, Transacao.TipoTransacao.RECEITA, inicio, fimMes));
        }

        BigDecimal receitasSalariaisConfirmadas = nz(
            transacaoRepository.sumReceitaSalarialConfirmadaPeriodo(usuarioId, inicio, fimMes));
        BigDecimal receitasPrevistas = calcularReceitasPrevistasMes(
            usuarioId, rendaLiquida, receitasSalariaisConfirmadas, diaAtual, diasNoMes, hoje);

        BigDecimal despesasPrevistas;
        if (ProjecaoMesCaixaSupport.usarModoAntiSusto(diaAtual, forecastProjecaoConfig.getDiaLiminarAntiSusto())) {
            BigDecimal fixasRestantes = nz(despesaFixaService.somarValorRestanteNoMes(usuarioId, hoje));
            BigDecimal parcelasEmprestimo = nz(transacaoRepository.sumParcelasEmprestimoPrevistasNoMes(
                usuarioId, hoje.atStartOfDay(), fimMes));
            despesasPrevistas = ProjecaoMesCaixaSupport.calcularDespesasPrevistasAntiSusto(
                mediaDiaria, diaAtual, diasNoMes, fixasRestantes, parcelasEmprestimo,
                forecastProjecaoConfig.getMargemVariavelPct());
            gastoProjetado = gastoAtual.add(despesasPrevistas).setScale(2, RoundingMode.HALF_UP);
        } else {
            despesasPrevistas = gastoProjetado.subtract(gastoAtual).max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal receitasFiscaisBrutas = nz(
            planejamentoFiscalService.somarReceitasPrevistasNoMes(usuarioId, ym));
        BigDecimal receitasFiscaisPrevistas = calcularReceitasFiscaisLiquidasNoMes(
            usuarioId, ym, inicio, fimMes, receitasFiscaisBrutas);

        BigDecimal patrimonio = patrimonioInicialOverride != null
            ? nz(patrimonioInicialOverride)
            : nz(patrimonioLiquido(usuarioId));
        BigDecimal saldoProjetado = patrimonio
            .add(receitasPrevistas)
            .add(receitasFiscaisPrevistas)
            .subtract(despesasPrevistas)
            .setScale(2, RoundingMode.HALF_UP);

        return new ProjecaoMesCaixa(
            ym, patrimonio, gastoAtual, gastoProjetado, rendaLiquida,
            receitasPrevistas, receitasFiscaisPrevistas, despesasPrevistas,
            saldoProjetado, diaAtual, diasNoMes
        );
    }

    /** M+1, M+2… — patrimônio inicial = saldo cascata; burn rate = média do mês corrente. */
    private ProjecaoMesCaixa calcularProjecaoMesFuturo(
        Long usuarioId,
        YearMonth ym,
        BigDecimal patrimonioInicial,
        BigDecimal mediaDiariaReferencia
    ) {
        LocalDateTime inicio = ym.atDay(1).atStartOfDay();
        LocalDateTime fimMes = ym.atEndOfMonth().atTime(23, 59, 59);
        int diasNoMes = ym.lengthOfMonth();

        BigDecimal gastoAtual = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal gastoProjetado = mediaDiariaReferencia.multiply(BigDecimal.valueOf(diasNoMes))
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal despesasPrevistas = gastoProjetado;

        BigDecimal rendaLiquida = rendaConfigService.getRendaMensalEstimada(usuarioId);
        if (rendaLiquida == null || rendaLiquida.compareTo(BigDecimal.ZERO) <= 0) {
            rendaLiquida = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal receitasSalariaisConfirmadas = nz(
            transacaoRepository.sumReceitaSalarialConfirmadaPeriodo(usuarioId, inicio, fimMes));
        BigDecimal receitasPrevistas = calcularReceitasPrevistasMes(
            usuarioId, rendaLiquida, receitasSalariaisConfirmadas, 1, diasNoMes, ym.atDay(1));

        BigDecimal receitasFiscaisBrutas = nz(
            planejamentoFiscalService.somarReceitasPrevistasNoMes(usuarioId, ym));
        BigDecimal receitasFiscaisPrevistas = calcularReceitasFiscaisLiquidasNoMes(
            usuarioId, ym, inicio, fimMes, receitasFiscaisBrutas);

        BigDecimal patrimonio = nz(patrimonioInicial);
        BigDecimal saldoProjetado = patrimonio
            .add(receitasPrevistas)
            .add(receitasFiscaisPrevistas)
            .subtract(despesasPrevistas)
            .setScale(2, RoundingMode.HALF_UP);

        return new ProjecaoMesCaixa(
            ym, patrimonio, gastoAtual, gastoProjetado, rendaLiquida,
            receitasPrevistas, receitasFiscaisPrevistas, despesasPrevistas,
            saldoProjetado, 1, diasNoMes
        );
    }

    private BigDecimal calcularReceitasPrevistasMes(
        Long usuarioId,
        BigDecimal rendaLiquida,
        BigDecimal receitasSalariaisConfirmadas,
        int diaAtual,
        int diasNoMes,
        LocalDate referencia
    ) {
        TipoConfiguracaoRenda tipo = rendaConfigService.obterDto(usuarioId)
            .map(RendaConfigDTO::getTipoConfiguracaoRenda)
            .orElse(TipoConfiguracaoRenda.CONTRACHEQUE);
        if (tipo == TipoConfiguracaoRenda.FLUXO_DIARIO) {
            int diasRestantes = Math.max(0, diasNoMes - diaAtual);
            return rendaLiquida.multiply(BigDecimal.valueOf(diasRestantes))
                .divide(BigDecimal.valueOf(Math.max(1, diasNoMes)), 2, RoundingMode.HALF_UP);
        }
        Integer diaPagamentoCfg = rendaConfigService.obterDto(usuarioId)
            .map(RendaConfigDTO::getDiaPagamento)
            .orElse(null);
        int diaPagamento = (diaPagamentoCfg != null && diaPagamentoCfg >= 1)
            ? diaPagamentoCfg
            : SalarioAutomaticoService.DIA_PAGAMENTO_PADRAO;
        return ProjecaoMesCaixaSupport.calcularGapSalarial(
            rendaLiquida,
            receitasSalariaisConfirmadas,
            referencia.getDayOfMonth(),
            diasNoMes,
            diaPagamento
        );
    }

    /**
     * Receitas fiscais previstas líquidas — fantasmas e 13º já confirmado no mês (evita double-dipping).
     */
    private BigDecimal calcularReceitasFiscaisLiquidasNoMes(
        Long usuarioId,
        YearMonth ym,
        LocalDateTime inicio,
        LocalDateTime fim,
        BigDecimal receitasFiscaisBrutas
    ) {
        BigDecimal decimoPrevisto = nz(transacaoRepository.sumReceitaDecimoTerceiroPrevistaPeriodo(
            usuarioId, inicio, fim));
        BigDecimal decimoConfirmado = nz(transacaoRepository.sumReceitaDecimoTerceiroConfirmadaPeriodo(
            usuarioId, inicio, fim));
        BigDecimal fantasmasBruto = conciliacaoAuditoriaService.receitasFiscaisLiquidasNoMes(
            usuarioId, ym, receitasFiscaisBrutas);
        BigDecimal fantasmas = receitasFiscaisBrutas.subtract(fantasmasBruto).max(BigDecimal.ZERO);
        return ProjecaoMesCaixaSupport.deduplicarReceitasFiscais(
            receitasFiscaisBrutas, decimoPrevisto, decimoConfirmado, fantasmas);
    }

    private static String formatarRotuloMes(YearMonth ym) {
        String mes = ym.getMonth().getDisplayName(TextStyle.FULL, new Locale("pt", "BR"));
        return mes.substring(0, 1).toUpperCase() + mes.substring(1) + "/" + ym.getYear();
    }

    /**
     * Receitas fiscais PREVISTO no mês (13º/IR). Confirmadas em conta já compõem {@link #patrimonioLiquido}
     * e não entram aqui — a query filtra {@code statusConferencia = PREVISTO}.
     */
    @Transactional(readOnly = true)
    public BigDecimal receitasFiscaisPrevistasNoMes(Long usuarioId, YearMonth ym) {
        return nz(planejamentoFiscalService.somarReceitasPrevistasNoMes(usuarioId, ym));
    }

    /**
     * Impacto adicional de uma nova despesa na projeção (evita duplicar o que já está no patrimônio).
     * Confirmada em conta sem fatura: já debitada via {@link SaldoMovimentacaoService} → zero.
     * Pendente ou só cartão/fatura: reduz a projeção pelo valor.
     */
    @Transactional(readOnly = true)
    public BigDecimal deltaProjecaoNovaDespesa(Transacao novaDespesa) {
        if (novaDespesa == null || novaDespesa.getTipoTransacao() != Transacao.TipoTransacao.DESPESA) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (impactoJaRefletidoNoPatrimonio(novaDespesa)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return nz(novaDespesa.getValor());
    }

    /** Movimento líquido confirmado em conta (sem fatura) no período — útil para ancorar gráficos. */
    @Transactional(readOnly = true)
    public BigDecimal movimentoLiquidoContaConfirmadoPeriodo(Long usuarioId, LocalDateTime inicio, LocalDateTime fim) {
        BigDecimal v = transacaoRepository.sumMovimentoLiquidoContaConfirmadaPeriodo(usuarioId, inicio, fim);
        return nz(v);
    }

    static boolean impactoJaRefletidoNoPatrimonio(Transacao t) {
        return t != null
            && t.getStatusConferencia() == Transacao.StatusConferencia.CONFIRMADA
            && t.getContaBancaria() != null
            && t.getFatura() == null;
    }

    /**
     * Alias explícito (receitas − despesas confirmadas — legado sem carteiras cadastradas).
     */
    @Transactional(readOnly = true)
    public BigDecimal saldoConfirmado(Long usuarioId) {
        BigDecimal r = transacaoRepository.sumValorConfirmadaByUsuarioIdAndTipoTransacao(
            usuarioId, Transacao.TipoTransacao.RECEITA);
        BigDecimal d = transacaoRepository.sumValorConfirmadaByUsuarioIdAndTipoTransacao(
            usuarioId, Transacao.TipoTransacao.DESPESA);
        BigDecimal i = transacaoRepository.sumValorConfirmadaByUsuarioIdAndTipoTransacao(
            usuarioId, Transacao.TipoTransacao.INVESTIMENTO);
        r = r != null ? r : BigDecimal.ZERO;
        d = d != null ? d : BigDecimal.ZERO;
        i = i != null ? i : BigDecimal.ZERO;
        return r.subtract(d).subtract(i);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Recalcula {@link ContaBancaria#getSaldoAtual()} a partir de {@code saldo_inicial}
     * + soma das transações confirmadas elegíveis — idempotente e seguro para reparos retroativos.
     */
    @Transactional
    public ResultadoReconciliacaoSaldo reconciliarSaldo(Long contaId, Long usuarioId) {
        ContaBancaria conta = contaBancariaService.buscarEntidade(contaId, usuarioId);
        BigDecimal saldoAnterior = nz(conta.getSaldoAtual());
        BigDecimal saldoInicial = conta.getSaldoInicial();
        if (saldoInicial == null) {
            saldoInicial = saldoAnterior;
            conta.setSaldoInicial(saldoInicial);
            contaBancariaRepository.save(conta);
        }
        BigDecimal saldoCalculado = nz(saldoInicial);
        int consideradas = 0;
        for (Transacao t : transacaoRepository.findEfetivadasPorConta(contaId)) {
            if (saldoMovimentacaoService.impactaSaldo(t)) {
                saldoCalculado = saldoCalculado.add(saldoMovimentacaoService.deltaSaldo(t));
                consideradas++;
            }
        }
        saldoCalculado = saldoCalculado.setScale(2, RoundingMode.HALF_UP);
        saldoMovimentacaoService.definirSaldoReconciliado(contaId, saldoCalculado);
        log.info("[SALDO] Reconciliação conta {} user {} — {} → {} ({} tx)",
            contaId, usuarioId, saldoAnterior, saldoCalculado, consideradas);
        return new ResultadoReconciliacaoSaldo(contaId, saldoAnterior, saldoCalculado, consideradas);
    }

    public void notificarAlteracaoSaldo(Long usuarioId) {
        if (usuarioId == null) {
            return;
        }
        BigDecimal s = patrimonioLiquido(usuarioId);
        log.debug("[SALDO] Utilizador {} — patrimônio líquido: {}", usuarioId, s);
    }

    public Optional<AuditoriaLiquidez> analisarDinheiroParado(Long usuarioId) {
        BigDecimal saldo = saldoContaCorrente(usuarioId);
        if (saldo.compareTo(BigDecimal.valueOf(1000)) < 0) {
            return Optional.empty();
        }
        LocalDateTime agora = LocalDateTime.now();
        List<Fatura> faturas = faturaRepository.findProximasNaoPagas(usuarioId, agora, agora.plusDays(30));
        if (faturas.isEmpty()) {
            return Optional.empty();
        }
        Fatura proxima = faturas.get(0);
        long dias = ChronoUnit.DAYS.between(LocalDate.now(), proxima.getDataVencimento().toLocalDate());
        if (dias <= 5) {
            return Optional.empty();
        }
        BigDecimal valorAplicavel = saldo.min(proxima.getValorFatura() != null ? proxima.getValorFatura() : saldo);
        BigDecimal ganhoEstimado = valorAplicavel
            .multiply(BigDecimal.valueOf(0.001))
            .multiply(BigDecimal.valueOf(dias))
            .setScale(2, RoundingMode.HALF_UP);
        return Optional.of(new AuditoriaLiquidez(
            saldo.setScale(2, RoundingMode.HALF_UP),
            valorAplicavel.setScale(2, RoundingMode.HALF_UP),
            ganhoEstimado,
            proxima.getDataVencimento().toLocalDate(),
            Math.max(1, dias - 1)
        ));
    }

    public Optional<OportunidadeInvestimento> sugerirInvestimentoSaldo(Long usuarioId) {
        BigDecimal saldo = saldoContaCorrente(usuarioId);
        if (saldo.compareTo(BigDecimal.valueOf(500)) < 0) {
            return Optional.empty();
        }
        BigDecimal valor = saldo.min(BigDecimal.valueOf(10000)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal poupanca = rendimento(valor, "0.0055");
        BigDecimal selic = rendimento(valor, "0.0085");
        BigDecimal cdb = rendimento(valor, "0.0092");
        String melhor = cdb.compareTo(selic) >= 0 ? "CDB de liquidez diária" : "Tesouro Selic";
        String textoIa;
        try {
            textoIa = openAiService.gerarTexto(usuarioId,
                "Explique em português brasileiro, em até 3 frases, uma simulação educativa de investimento. "
                    + "Não prometa rentabilidade e diga que não é recomendação individual.",
                "Saldo ocioso R$ " + valor + ". Poupança R$ " + poupanca
                    + ", Tesouro Selic R$ " + selic + ", CDB liquidez diária R$ " + cdb + ".",
                "Esta é uma simulação educativa, não uma recomendação individual. Compare liquidez, risco e prazo antes de aplicar.");
        } catch (Exception e) {
            textoIa = "Esta é uma simulação educativa, não uma recomendação individual. Compare liquidez, risco e prazo antes de aplicar.";
        }
        return Optional.of(new OportunidadeInvestimento(valor, poupanca, selic, cdb, melhor, textoIa));
    }

    private static BigDecimal rendimento(BigDecimal valor, String taxaMensal) {
        return valor.multiply(new BigDecimal(taxaMensal)).setScale(2, RoundingMode.HALF_UP);
    }

    public record AuditoriaLiquidez(
        BigDecimal saldoDisponivel,
        BigDecimal valorAplicavel,
        BigDecimal ganhoEstimado,
        LocalDate vencimentoFatura,
        long diasParaResgate
    ) {
        public String mensagem() {
            return "Vi que você tem R$ " + saldoDisponivel + " parados. Se colocar R$ "
                + valorAplicavel + " num CDB hoje, pode ganhar aproximadamente R$ "
                + ganhoEstimado + " até o vencimento da sua fatura no dia "
                + vencimentoFatura.getDayOfMonth() + ". Quer que eu te lembre de resgatar no dia "
                + vencimentoFatura.minusDays(1).getDayOfMonth() + "?";
        }
    }

    public record OportunidadeInvestimento(
        BigDecimal saldoOcioso,
        BigDecimal rendimentoPoupanca,
        BigDecimal rendimentoTesouroSelic,
        BigDecimal rendimentoCdb,
        String melhorOpcao,
        String explicacaoIa
    ) {
        public String mensagemWhatsApp() {
            return "Você tem R$ " + saldoOcioso + " parados. Na Poupança renderia cerca de R$ "
                + rendimentoPoupanca + ". No Tesouro Selic renderia cerca de R$ " + rendimentoTesouroSelic
                + ". No CDB de liquidez diária renderia cerca de R$ " + rendimentoCdb
                + ". Melhor simulação do dia: *" + melhorOpcao
                + "*. Deseja simular o impacto disso no seu Score de Saúde Financeira?\n\n"
                + explicacaoIa;
        }
    }
}
