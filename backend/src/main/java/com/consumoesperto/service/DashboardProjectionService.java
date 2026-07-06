package com.consumoesperto.service;

import com.consumoesperto.dto.DashboardProjectionDTO;
import com.consumoesperto.dto.RendaConfigDTO;
import com.consumoesperto.dto.SimulacaoImpactoDTO;
import com.consumoesperto.dto.TimelineImpactoDTO;
import com.consumoesperto.model.MetaFinanceira;
import com.consumoesperto.model.TipoConfiguracaoRenda;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.MetaFinanceiraRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.util.AppTimeZone;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.consumoesperto.util.MoedaUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardProjectionService {

    private final TransacaoRepository transacaoRepository;
    private final SimulacaoImpactoService simulacaoImpactoService;
    private final MetaFinanceiraRepository metaFinanceiraRepository;
    private final SaldoService saldoService;
    private final RendaConfigService rendaConfigService;

    @Transactional(readOnly = true)
    public DashboardProjectionDTO projetar(Long usuarioId) {
        YearMonth ym = YearMonth.from(AppTimeZone.hoje());
        LocalDate hoje = AppTimeZone.hoje();
        LocalDateTime inicioMes = ym.atDay(1).atStartOfDay();
        LocalDateTime fimMes = ym.atEndOfMonth().atTime(23, 59, 59);

        List<Transacao> transacoes = transacaoRepository.findByUsuarioIdAndDataTransacaoBetween(
            usuarioId, inicioMes, fimMes
        );
        List<String> labels = new ArrayList<>();
        List<BigDecimal> real = new ArrayList<>();
        List<BigDecimal> projetado = new ArrayList<>();
        List<BigDecimal> simulado = new ArrayList<>();

        BigDecimal saldo = BigDecimal.ZERO;
        BigDecimal gastoAteHoje = BigDecimal.ZERO;
        for (int d = 1; d <= ym.lengthOfMonth(); d++) {
            LocalDate dia = ym.atDay(d);
            labels.add(String.valueOf(d));
            BigDecimal deltaDia = deltaDoDia(transacoes, dia);
            if (!dia.isAfter(hoje)) {
                saldo = saldo.add(deltaDia);
                real.add(saldo.setScale(2, RoundingMode.HALF_UP));
                projetado.add(saldo.setScale(2, RoundingMode.HALF_UP));
                simulado.add(saldo.setScale(2, RoundingMode.HALF_UP));
                gastoAteHoje = gastoAteHoje.add(gastoDoDia(transacoes, dia));
            } else {
                real.add(null);
                projetado.add(null);
                simulado.add(null);
            }
        }

        int diasDecorridos = Math.max(1, hoje.getDayOfMonth());
        BigDecimal gastoMedioDiario = gastoAteHoje.divide(BigDecimal.valueOf(diasDecorridos), 2, RoundingMode.HALF_UP);
        BigDecimal impactoSimuladoDiario = simulacaoImpactoService.impactoMensalAtivo(usuarioId)
            .divide(BigDecimal.valueOf(ym.lengthOfMonth()), 2, RoundingMode.HALF_UP);

        BigDecimal patrimonioHoje = saldoService.patrimonioLiquido(usuarioId);
        int idxHoje = Math.min(hoje.getDayOfMonth(), real.size()) - 1;
        BigDecimal acumuladoMesAteHoje = idxHoje >= 0 && real.get(idxHoje) != null
            ? real.get(idxHoje) : BigDecimal.ZERO;
        BigDecimal offsetPatrimonio = patrimonioHoje.subtract(acumuladoMesAteHoje);
        for (int i = 0; i < hoje.getDayOfMonth() && i < real.size(); i++) {
            if (real.get(i) != null) {
                BigDecimal ancorado = real.get(i).add(offsetPatrimonio).setScale(2, RoundingMode.HALF_UP);
                real.set(i, ancorado);
                projetado.set(i, ancorado);
                simulado.set(i, ancorado);
            }
        }

        Map<Integer, BigDecimal> receitasPorDia = mapaReceitasEsperadas(usuarioId, ym, hoje, inicioMes, fimMes);

        BigDecimal saldoProjetado = projetado.get(Math.min(hoje.getDayOfMonth(), projetado.size()) - 1);
        BigDecimal saldoSimulado = simulado.get(Math.min(hoje.getDayOfMonth(), simulado.size()) - 1);
        for (int i = hoje.getDayOfMonth(); i < ym.lengthOfMonth(); i++) {
            int dia = i + 1;
            BigDecimal receitaDia = receitasPorDia.getOrDefault(dia, BigDecimal.ZERO);
            saldoProjetado = saldoProjetado.subtract(gastoMedioDiario).add(receitaDia);
            saldoSimulado = saldoSimulado.subtract(gastoMedioDiario).subtract(impactoSimuladoDiario).add(receitaDia);
            projetado.set(i, saldoProjetado.setScale(2, RoundingMode.HALF_UP));
            simulado.set(i, saldoSimulado.setScale(2, RoundingMode.HALF_UP));
        }

        DashboardProjectionDTO dto = new DashboardProjectionDTO();
        dto.setLabels(labels);
        dto.setReal(real);
        dto.setProjetado(projetado);
        dto.setSimulado(simulado);
        dto.setSimulacoesAtivas(simulacaoImpactoService.listarAtivas(usuarioId));
        dto.setTimelineImpacto(timeline(usuarioId));
        dto.setSafraPatrimonio(saldoService.calcularProjecaoSafraDto(usuarioId, 2));
        return dto;
    }

    /** Receitas esperadas no restante do mês: gap salarial + recorrentes vincendas. */
    private Map<Integer, BigDecimal> mapaReceitasEsperadas(
        Long usuarioId,
        YearMonth ym,
        LocalDate hoje,
        LocalDateTime inicioMes,
        LocalDateTime fimMes
    ) {
        Map<Integer, BigDecimal> map = new HashMap<>();
        BigDecimal rendaLiquida = rendaConfigService.getRendaMensalEstimada(usuarioId);
        if (rendaLiquida == null || rendaLiquida.compareTo(BigDecimal.ZERO) <= 0) {
            rendaLiquida = nz(transacaoRepository.sumReceitasConfirmadasPeriodo(usuarioId, inicioMes, fimMes));
        }
        BigDecimal receitasSalariaisConfirmadas = nz(
            transacaoRepository.sumReceitaSalarialConfirmadaPeriodo(usuarioId, inicioMes, fimMes));

        TipoConfiguracaoRenda tipo = rendaConfigService.obterDto(usuarioId)
            .map(RendaConfigDTO::getTipoConfiguracaoRenda)
            .orElse(TipoConfiguracaoRenda.CONTRACHEQUE);
        if (tipo == TipoConfiguracaoRenda.FLUXO_DIARIO) {
            int diasRestantes = Math.max(0, ym.lengthOfMonth() - hoje.getDayOfMonth());
            if (diasRestantes > 0) {
                BigDecimal diaria = rendaLiquida.divide(
                    BigDecimal.valueOf(ym.lengthOfMonth()), 2, RoundingMode.HALF_UP);
                for (int d = hoje.getDayOfMonth() + 1; d <= ym.lengthOfMonth(); d++) {
                    map.merge(d, diaria, BigDecimal::add);
                }
            }
        } else {
            Integer diaPagamentoCfg = rendaConfigService.obterDto(usuarioId)
                .map(RendaConfigDTO::getDiaPagamento)
                .orElse(null);
            int diaPagamento = (diaPagamentoCfg != null && diaPagamentoCfg >= 1)
                ? diaPagamentoCfg
                : SalarioAutomaticoService.DIA_PAGAMENTO_PADRAO;
            int diaEfetivo = Math.min(diaPagamento, ym.lengthOfMonth());
            if (diaEfetivo > hoje.getDayOfMonth()) {
                BigDecimal gap = ProjecaoMesCaixaSupport.calcularGapSalarial(
                    rendaLiquida,
                    receitasSalariaisConfirmadas,
                    hoje.getDayOfMonth(),
                    ym.lengthOfMonth(),
                    diaPagamento
                );
                if (gap.compareTo(BigDecimal.ZERO) > 0) {
                    map.merge(diaEfetivo, gap, BigDecimal::add);
                }
            }
        }

        LocalDate fimMesDate = ym.atEndOfMonth();
        transacaoRepository.findByUsuarioIdAndRecorrenteIsTrueAndTipoTransacao(
                usuarioId, Transacao.TipoTransacao.RECEITA).stream()
            .filter(t -> t.getProximaExecucao() != null
                && !t.getProximaExecucao().isBefore(hoje.plusDays(1))
                && !t.getProximaExecucao().isAfter(fimMesDate))
            .forEach(t -> {
                int dia = t.getProximaExecucao().getDayOfMonth();
                map.merge(dia, nz(t.getValor()), BigDecimal::add);
            });

        for (Map.Entry<Integer, BigDecimal> e : map.entrySet()) {
            e.setValue(e.getValue().setScale(2, RoundingMode.HALF_UP));
        }
        return map;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private List<TimelineImpactoDTO> timeline(Long usuarioId) {
        BigDecimal impacto = simulacaoImpactoService.impactoMensalAtivo(usuarioId);
        List<MetaFinanceira> metas = metaFinanceiraRepository.findByUsuarioIdOrderByPrioridadeDescDataCriacaoDesc(usuarioId);
        List<TimelineImpactoDTO> out = new ArrayList<>();
        for (MetaFinanceira m : metas.stream().limit(4).toList()) {
            int original = m.getPrazoMeses() != null ? m.getPrazoMeses().setScale(0, RoundingMode.CEILING).intValue() : 0;
            BigDecimal poupanca = m.getValorPoupadoMensal() != null ? m.getValorPoupadoMensal() : BigDecimal.ZERO;
            int atraso = BigDecimal.ZERO.compareTo(impacto) < 0 && poupanca.compareTo(BigDecimal.ZERO) > 0
                ? impacto.divide(poupanca, 0, RoundingMode.CEILING).intValue()
                : 0;
            TimelineImpactoDTO t = new TimelineImpactoDTO();
            t.setTitulo(m.getDescricao());
            t.setIcone(iconeParaMeta(m.getDescricao()));
            t.setMesesOriginais(original);
            t.setMesesProjetados(original + atraso);
            t.setDeslocamentoMeses(atraso);
            out.add(t);
        }
        return out;
    }

    private static BigDecimal deltaDoDia(List<Transacao> transacoes, LocalDate dia) {
        return transacoes.stream()
            .filter(t -> t.getDataTransacao() != null && t.getDataTransacao().toLocalDate().equals(dia))
            .filter(t -> t.getStatusConferencia() == Transacao.StatusConferencia.CONFIRMADA)
            .map(t -> t.getTipoTransacao() == Transacao.TipoTransacao.RECEITA
                ? MoedaUtil.nz(t.getValor()) : MoedaUtil.nz(t.getValor()).negate())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal gastoDoDia(List<Transacao> transacoes, LocalDate dia) {
        return transacoes.stream()
            .filter(t -> t.getDataTransacao() != null && t.getDataTransacao().toLocalDate().equals(dia))
            .filter(t -> t.getStatusConferencia() == Transacao.StatusConferencia.CONFIRMADA)
            .filter(t -> t.getTipoTransacao() == Transacao.TipoTransacao.DESPESA)
            .map(t -> MoedaUtil.nz(t.getValor()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String iconeParaMeta(String descricao) {
        String d = descricao != null ? descricao.toLowerCase() : "";
        if (d.contains("viagem") || d.contains("viajar")) return "plane";
        if (d.contains("casa")) return "home";
        if (d.contains("carro")) return "car";
        if (d.contains("reserva")) return "shield-alt";
        return "bullseye";
    }
}
