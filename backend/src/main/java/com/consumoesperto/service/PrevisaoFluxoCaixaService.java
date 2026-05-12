package com.consumoesperto.service;

import com.consumoesperto.dto.DisponibilidadeRealDTO;
import com.consumoesperto.dto.MarketIndicatorsDTO;
import com.consumoesperto.dto.PrevisaoFuturoChartDTO;
import com.consumoesperto.dto.ProvisaoMemoriaDTO;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Camada Sentinela: projeção de fluxo de caixa (saldo − obrigações) e série para gráfico "futuro provável".
 */
@Service
@RequiredArgsConstructor
public class PrevisaoFluxoCaixaService {

    private static final int DIAS_AMOSTRA_BURN = 30;

    private final SaldoService saldoService;
    private final FaturaRepository faturaRepository;
    private final TransacaoRepository transacaoRepository;
    private final RecurringExpenseDetectionService recurringExpenseDetectionService;
    private final JarvisProtocolService jarvisProtocolService;
    private final UsuarioRepository usuarioRepository;
    private final DespesaFixaService despesaFixaService;
    private final MarketDataService marketDataService;
    private final ProvisaoMemoriaSentinelaService provisaoMemoriaSentinelaService;

    @Transactional(readOnly = true)
    public DisponibilidadeRealDTO calcularDisponibilidadeReal(Long usuarioId) {
        BigDecimal saldo = nz(saldoService.saldoContaCorrente(usuarioId));
        BigDecimal fixas = somarContasFixasRestantesNoMes(usuarioId, LocalDate.now())
            .add(despesaFixaService.somarValorRestanteNoMes(usuarioId, LocalDate.now()));
        BigDecimal faturas = nz(faturaRepository.sumValorFaturasPendentesByUsuarioId(usuarioId));
        BigDecimal obrig = fixas.add(faturas);
        BigDecimal disponivel = saldo.subtract(obrig);

        BigDecimal pct;
        if (saldo.compareTo(BigDecimal.ZERO) <= 0) {
            pct = BigDecimal.ZERO;
        } else {
            pct = disponivel
                .divide(saldo, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
        }

        YearMonth ym = YearMonth.now();
        int diasRest = ym.lengthOfMonth() - LocalDate.now().getDayOfMonth() + 1;

        String voc = jarvisProtocolService.resolveVocative(usuarioId, usuarioRepository);
        String msg = jarvisProtocolService.proativoDisponibilidadeReal(
            voc,
            pct,
            diasRest,
            saldo,
            fixas,
            faturas,
            disponivel
        );

        return new DisponibilidadeRealDTO(saldo, fixas, faturas, obrig, disponivel, pct, diasRest, msg);
    }

    @Transactional(readOnly = true)
    public String montarRelatorioDisponibilidadeWhatsapp(Long usuarioId) {
        return calcularDisponibilidadeReal(usuarioId).getMensagemJarvis();
    }

    /**
     * Burn diário agregado (conta + rateio de obrigações no mês), antes de ajustes de protocolo.
     */
    @Transactional(readOnly = true)
    public BigDecimal calcularBurnTotalDiario(Long usuarioId) {
        LocalDate hoje = LocalDate.now();
        YearMonth ym = YearMonth.from(hoje);
        int ultimo = ym.lengthOfMonth();
        int diaHoje = hoje.getDayOfMonth();

        LocalDateTime iniBurn = hoje.minusDays(DIAS_AMOSTRA_BURN).atStartOfDay();
        LocalDateTime fimBurn = hoje.plusDays(1).atStartOfDay();
        BigDecimal gastoConta30d = nz(transacaoRepository.sumDespesaContaCorrenteConfirmadaPeriodo(
            usuarioId, iniBurn, fimBurn));
        BigDecimal burnDiario = gastoConta30d.divide(BigDecimal.valueOf(DIAS_AMOSTRA_BURN), 4, RoundingMode.HALF_UP);

        BigDecimal fixasRest = somarContasFixasRestantesNoMes(usuarioId, hoje);
        BigDecimal despesasFixasRest = despesaFixaService.somarValorRestanteNoMes(usuarioId, hoje);
        BigDecimal faturas = nz(faturaRepository.sumValorFaturasPendentesByUsuarioId(usuarioId));
        int diasParaFim = ultimo - diaHoje + 1;
        BigDecimal obrigDiaria = diasParaFim > 0
            ? fixasRest.add(faturas).add(despesasFixasRest).divide(BigDecimal.valueOf(diasParaFim), 4, RoundingMode.HALF_UP)
            : fixasRest.add(faturas).add(despesasFixasRest);

        return burnDiario.add(obrigDiaria).max(new BigDecimal("0.01"));
    }

    @Transactional(readOnly = true)
    public PrevisaoFuturoChartDTO buildPrevisaoFuturoChart(Long usuarioId) {
        PrevisaoFuturoChartDTO dto = buildPrevisaoFuturoChartInternal(usuarioId, BigDecimal.ZERO);
        enrichProtocoloDiagnostics(dto, usuarioId);
        enriquecerNotaMercado(dto, usuarioId);
        return dto;
    }

    /**
     * Mesma lógica da sentinela, reduzindo o burn diário (economia disciplinar pós-ajuste de tetos).
     */
    @Transactional(readOnly = true)
    public PrevisaoFuturoChartDTO buildPrevisaoFuturoChartComEconomiaDiaria(Long usuarioId, BigDecimal economiaDiaria) {
        BigDecimal sub = economiaDiaria != null ? economiaDiaria.max(BigDecimal.ZERO) : BigDecimal.ZERO;
        PrevisaoFuturoChartDTO dto = buildPrevisaoFuturoChartInternal(usuarioId, sub);
        enrichProtocoloDiagnostics(dto, usuarioId);
        enriquecerNotaMercado(dto, usuarioId);
        return dto;
    }

    private PrevisaoFuturoChartDTO buildPrevisaoFuturoChartInternal(Long usuarioId, BigDecimal reducaoBurnDiaria) {
        LocalDate hoje = LocalDate.now();
        YearMonth ym = YearMonth.from(hoje);
        int ultimo = ym.lengthOfMonth();
        int diaHoje = hoje.getDayOfMonth();

        BigDecimal saldoAtual = nz(saldoService.saldoContaCorrente(usuarioId));

        LocalDateTime iniBurn = hoje.minusDays(DIAS_AMOSTRA_BURN).atStartOfDay();
        LocalDateTime fimBurn = hoje.plusDays(1).atStartOfDay();
        BigDecimal gastoConta30d = nz(transacaoRepository.sumDespesaContaCorrenteConfirmadaPeriodo(
            usuarioId, iniBurn, fimBurn));
        BigDecimal burnDiario = gastoConta30d.divide(BigDecimal.valueOf(DIAS_AMOSTRA_BURN), 4, RoundingMode.HALF_UP);

        BigDecimal fixasRest = somarContasFixasRestantesNoMes(usuarioId, hoje);
        BigDecimal faturas = nz(faturaRepository.sumValorFaturasPendentesByUsuarioId(usuarioId));
        int diasParaFim = ultimo - diaHoje + 1;
        BigDecimal obrigDiaria = diasParaFim > 0
            ? fixasRest.add(faturas).divide(BigDecimal.valueOf(diasParaFim), 4, RoundingMode.HALF_UP)
            : fixasRest.add(faturas);

        Map<Integer, BigDecimal> saltosDespesasFixas = despesaFixaService.mapaSaltosProjetadosAposDia(usuarioId, hoje);
        List<Integer> marcos = new ArrayList<>(saltosDespesasFixas.keySet());

        MarketIndicatorsDTO mercado = marketDataService.buscarIndicadoresResiliente();
        BigDecimal fatorInfl = marketDataService.fatorCorrecaoConsumoRecorrente(mercado);

        BigDecimal burnTotalDiario = burnDiario.add(obrigDiaria).max(new BigDecimal("0.01"));
        burnTotalDiario = burnTotalDiario.multiply(fatorInfl).setScale(4, RoundingMode.HALF_UP).max(new BigDecimal("0.01"));
        BigDecimal sub = reducaoBurnDiaria != null ? reducaoBurnDiaria : BigDecimal.ZERO;
        burnTotalDiario = burnTotalDiario.subtract(sub).max(new BigDecimal("0.01"));

        List<ProvisaoMemoriaDTO> provisoes = provisaoMemoriaSentinelaService.calcularProvisoesParaMesAtual(usuarioId).stream()
            .filter(p -> p.getDiaAlvo() > diaHoje)
            .collect(Collectors.toList());
        List<Integer> diasProv = provisoes.stream()
            .map(ProvisaoMemoriaDTO::getDiaAlvo)
            .filter(d -> d > diaHoje && d <= ultimo)
            .distinct()
            .sorted()
            .collect(Collectors.toList());

        List<PrevisaoFuturoChartDTO.Ponto> pontos = new ArrayList<>();
        pontos.add(new PrevisaoFuturoChartDTO.Ponto(diaHoje, saldoAtual.setScale(2, RoundingMode.HALF_UP), "REAL"));

        BigDecimal saldoLinha = saldoAtual;
        boolean negativa = false;
        for (int d = diaHoje + 1; d <= ultimo; d++) {
            saldoLinha = saldoLinha.subtract(burnTotalDiario);
            BigDecimal saltoFixa = saltosDespesasFixas.getOrDefault(d, BigDecimal.ZERO);
            if (saltoFixa.compareTo(BigDecimal.ZERO) > 0) {
                saldoLinha = saldoLinha.subtract(saltoFixa);
            }
            for (ProvisaoMemoriaDTO pm : provisoes) {
                if (pm.getDiaAlvo() == d && pm.getValor() != null) {
                    saldoLinha = saldoLinha.subtract(pm.getValor());
                }
            }
            if (saldoLinha.compareTo(BigDecimal.ZERO) < 0) {
                negativa = true;
            }
            pontos.add(new PrevisaoFuturoChartDTO.Ponto(d, saldoLinha.setScale(2, RoundingMode.HALF_UP), "PROJETADO"));
        }

        BigDecimal fimMes = pontos.isEmpty() ? saldoAtual : pontos.get(pontos.size() - 1).getSaldo();

        PrevisaoFuturoChartDTO dto = new PrevisaoFuturoChartDTO();
        dto.setDiaHoje(diaHoje);
        dto.setUltimoDiaMes(ultimo);
        dto.setSaldoAtual(saldoAtual.setScale(2, RoundingMode.HALF_UP));
        dto.setSaldoProjetadoFimMes(fimMes);
        dto.setProjecaoNegativa(negativa || fimMes.compareTo(BigDecimal.ZERO) < 0);
        dto.setDiasVencimentoDespesasFixas(marcos);
        dto.setIndicadoresMercado(mercado);
        dto.setFatorCorrecaoInflacao(fatorInfl);
        dto.setProvisoesMemoria(provisoes);
        dto.setDiasProvisaoMemoria(diasProv);
        dto.setPontos(pontos);
        return dto;
    }

    private void enriquecerNotaMercado(PrevisaoFuturoChartDTO dto, Long usuarioId) {
        if (dto == null || usuarioId == null) {
            return;
        }
        String voc = jarvisProtocolService.resolveVocative(usuarioId, usuarioRepository);
        dto.setNotaJarvisMercado(jarvisProtocolService.notaOraculoMercado(voc, dto.getIndicadoresMercado(), dto.getFatorCorrecaoInflacao()));
    }

    private void enrichProtocoloDiagnostics(PrevisaoFuturoChartDTO dto, Long usuarioId) {
        YearMonth ym = YearMonth.now();
        LocalDateTime ini = ym.atDay(1).atStartOfDay();
        LocalDateTime fim = ym.atEndOfMonth().atTime(23, 59, 59);
        BigDecimal despesasMes = nz(transacaoRepository.sumConfirmadaByUsuarioIdAndTipoAndPeriodo(
            usuarioId, Transacao.TipoTransacao.DESPESA, ini, fim));
        BigDecimal saldo = nz(dto.getSaldoAtual());
        BigDecimal mesesEscudo = null;
        if (despesasMes.compareTo(BigDecimal.ZERO) > 0) {
            mesesEscudo = saldo.divide(despesasMes, 2, RoundingMode.HALF_UP);
        }
        dto.setMesesEscudoEnergia(mesesEscudo);

        Integer diasNeg = null;
        for (PrevisaoFuturoChartDTO.Ponto p : dto.getPontos()) {
            if ("PROJETADO".equals(p.getSerie()) && p.getSaldo() != null
                && p.getSaldo().compareTo(BigDecimal.ZERO) < 0) {
                diasNeg = p.getDia() - dto.getDiaHoje();
                break;
            }
        }
        dto.setDiasAteSaldoNegativo(diasNeg);

        boolean escudoCritico = mesesEscudo != null && mesesEscudo.compareTo(BigDecimal.valueOf(6)) < 0;
        boolean colisao15 = diasNeg != null && diasNeg <= 15;
        boolean recomendar = escudoCritico || colisao15 || dto.isProjecaoNegativa();
        dto.setProtocoloOtimizacaoRecomendado(recomendar);
    }

    private BigDecimal somarContasFixasRestantesNoMes(Long usuarioId, LocalDate referencia) {
        YearMonth ym = YearMonth.from(referencia);
        LocalDate fimMes = ym.atEndOfMonth();
        BigDecimal somaDetectadas = recurringExpenseDetectionService.detectar(usuarioId).stream()
            .filter(r -> !r.proximaData().isBefore(referencia) && !r.proximaData().isAfter(fimMes))
            .map(RecurringExpenseDetectionService.RecurringExpense::valorMedio)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (somaDetectadas.compareTo(BigDecimal.ZERO) > 0) {
            return somaDetectadas.setScale(2, RoundingMode.HALF_UP);
        }
        return transacaoRepository.findByUsuarioIdAndRecorrenteIsTrueAndTipoTransacao(
                usuarioId, com.consumoesperto.model.Transacao.TipoTransacao.DESPESA).stream()
            .filter(t -> t.getProximaExecucao() != null
                && !t.getProximaExecucao().isBefore(referencia)
                && !t.getProximaExecucao().isAfter(fimMes))
            .map(t -> nz(t.getValor()))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
