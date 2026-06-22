package com.consumoesperto.service;

import com.consumoesperto.dto.ParcelaReceitaFiscalDTO;
import com.consumoesperto.model.ContextoFinanceiro;
import com.consumoesperto.model.GravidadeAlertaFluxo;
import com.consumoesperto.model.RiscoFluxo;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sentinela v8 — simulação determinística de fluxo de caixa (90 dias).
 */
@Service
@RequiredArgsConstructor
public class AlertaTempestadeService {

    private static final int HORIZONTE_DIAS = 90;

    private final JarvisContextoFinanceiroService jarvisContextoFinanceiroService;
    private final SaldoService saldoService;
    private final TransacaoRepository transacaoRepository;
    private final DespesaFixaService despesaFixaService;
    private final PlanejamentoFiscalService planejamentoFiscalService;

    @Transactional(readOnly = true)
    public RiscoFluxo simular(Long usuarioId) {
        if (usuarioId == null) {
            return RiscoFluxo.nenhum();
        }
        ContextoFinanceiro ctx = jarvisContextoFinanceiroService.montarSnapshot(usuarioId);
        BigDecimal saldo = nz(ctx.getPatrimonioLiquido());
        if (saldo.compareTo(BigDecimal.ZERO) == 0) {
            saldo = nz(saldoService.saldoContaCorrente(usuarioId));
        }
        BigDecimal gastoMensal = ctx.getGastoMensalMedio();
        LocalDate hoje = LocalDate.now();
        LocalDate fim = hoje.plusDays(HORIZONTE_DIAS);

        Map<LocalDate, BigDecimal> entradas = carregarEntradasPrevistas(usuarioId, hoje, fim);
        Map<LocalDate, BigDecimal> saidas = carregarSaidasPrevistas(usuarioId, hoje, fim);

        RiscoFluxo risco = RiscoFluxo.nenhum();
        for (LocalDate dia = hoje; !dia.isAfter(fim); dia = dia.plusDays(1)) {
            saldo = saldo.add(entradas.getOrDefault(dia, BigDecimal.ZERO));
            saldo = saldo.subtract(saidas.getOrDefault(dia, BigDecimal.ZERO));

            BigDecimal escudoMeses = null;
            if (gastoMensal != null && gastoMensal.signum() > 0) {
                escudoMeses = saldo.divide(gastoMensal, 1, RoundingMode.HALF_UP);
            }
            if (saldo.signum() < 0) {
                risco.registrar(GravidadeAlertaFluxo.VERMELHO, dia, saldo);
            } else if (escudoMeses != null && escudoMeses.compareTo(BigDecimal.valueOf(2)) < 0) {
                risco.registrar(GravidadeAlertaFluxo.AMBAR, dia, escudoMeses);
            }
        }
        return risco;
    }

    private Map<LocalDate, BigDecimal> carregarEntradasPrevistas(Long usuarioId, LocalDate inicio, LocalDate fim) {
        Map<LocalDate, BigDecimal> map = new HashMap<>();
        LocalDateTime ini = inicio.atStartOfDay();
        LocalDateTime end = fim.atTime(23, 59, 59);
        List<Transacao> previstas = transacaoRepository.findPrevistasUsuarioEntre(usuarioId, ini, end);
        for (Transacao t : previstas) {
            if (t.getTipoTransacao() != Transacao.TipoTransacao.RECEITA || t.getDataTransacao() == null) {
                continue;
            }
            LocalDate d = t.getDataTransacao().toLocalDate();
            map.merge(d, nz(t.getValor()), BigDecimal::add);
        }
        YearMonth cursor = YearMonth.from(inicio);
        if (cursor.equals(YearMonth.from(LocalDate.now()))) {
            for (ParcelaReceitaFiscalDTO rf : planejamentoFiscalService.listarReceitasProjetadasMesAtual(usuarioId)) {
                if (rf.getValor() == null) {
                    continue;
                }
                int dia = Math.min(rf.getDia(), cursor.lengthOfMonth());
                LocalDate data = LocalDate.of(cursor.getYear(), cursor.getMonth(), dia);
                if (!data.isBefore(inicio) && !data.isAfter(fim)) {
                    map.merge(data, rf.getValor(), BigDecimal::add);
                }
            }
        }
        return map;
    }

    private Map<LocalDate, BigDecimal> carregarSaidasPrevistas(Long usuarioId, LocalDate inicio, LocalDate fim) {
        Map<LocalDate, BigDecimal> map = new HashMap<>();
        LocalDateTime ini = inicio.atStartOfDay();
        LocalDateTime end = fim.atTime(23, 59, 59);
        List<Transacao> previstas = transacaoRepository.findPrevistasUsuarioEntre(usuarioId, ini, end);
        for (Transacao t : previstas) {
            if (t.getTipoTransacao() != Transacao.TipoTransacao.DESPESA || t.getDataTransacao() == null) {
                continue;
            }
            LocalDate d = t.getDataTransacao().toLocalDate();
            map.merge(d, nz(t.getValor()), BigDecimal::add);
        }
        YearMonth cursor = YearMonth.from(inicio);
        YearMonth limite = YearMonth.from(fim);
        while (!cursor.isAfter(limite)) {
            LocalDate ref = cursor.atDay(1);
            if (!ref.isAfter(fim)) {
                Map<Integer, BigDecimal> saltos = despesaFixaService.mapaSaltosProjetadosAposDia(usuarioId, ref);
                for (Map.Entry<Integer, BigDecimal> e : saltos.entrySet()) {
                    int dia = e.getKey();
                    if (dia < 1 || dia > cursor.lengthOfMonth()) {
                        continue;
                    }
                    LocalDate data = LocalDate.of(cursor.getYear(), cursor.getMonth(), dia);
                    if (!data.isBefore(inicio) && !data.isAfter(fim)) {
                        map.merge(data, nz(e.getValue()), BigDecimal::add);
                    }
                }
            }
            cursor = cursor.plusMonths(1);
        }
        return map;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
