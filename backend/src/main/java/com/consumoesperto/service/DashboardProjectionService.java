package com.consumoesperto.service;

import com.consumoesperto.dto.DashboardProjectionDTO;
import com.consumoesperto.dto.SimulacaoImpactoDTO;
import com.consumoesperto.dto.TimelineImpactoDTO;
import com.consumoesperto.model.MetaFinanceira;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.MetaFinanceiraRepository;
import com.consumoesperto.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardProjectionService {

    private final TransacaoRepository transacaoRepository;
    private final SimulacaoImpactoService simulacaoImpactoService;
    private final MetaFinanceiraRepository metaFinanceiraRepository;

    @Transactional(readOnly = true)
    public DashboardProjectionDTO projetar(Long usuarioId) {
        YearMonth ym = YearMonth.now();
        LocalDate hoje = LocalDate.now();
        List<Transacao> transacoes = transacaoRepository.findByUsuarioIdAndDataTransacaoBetween(
            usuarioId,
            ym.atDay(1).atStartOfDay(),
            ym.atEndOfMonth().atTime(23, 59, 59)
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
        BigDecimal saldoProjetado = projetado.get(Math.min(hoje.getDayOfMonth(), projetado.size()) - 1);
        BigDecimal saldoSimulado = simulado.get(Math.min(hoje.getDayOfMonth(), simulado.size()) - 1);
        for (int i = hoje.getDayOfMonth(); i < ym.lengthOfMonth(); i++) {
            saldoProjetado = saldoProjetado.subtract(gastoMedioDiario);
            saldoSimulado = saldoSimulado.subtract(gastoMedioDiario).subtract(impactoSimuladoDiario);
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
        return dto;
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
            .map(t -> t.getTipoTransacao() == Transacao.TipoTransacao.RECEITA ? t.getValor() : t.getValor().negate())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal gastoDoDia(List<Transacao> transacoes, LocalDate dia) {
        return transacoes.stream()
            .filter(t -> t.getDataTransacao() != null && t.getDataTransacao().toLocalDate().equals(dia))
            .filter(t -> t.getTipoTransacao() == Transacao.TipoTransacao.DESPESA)
            .map(Transacao::getValor)
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
