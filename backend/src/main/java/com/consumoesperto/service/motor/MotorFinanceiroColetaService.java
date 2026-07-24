package com.consumoesperto.service.motor;

import com.consumoesperto.dto.OrcamentoDTO;
import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.MetaFinanceira;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.MetaFinanceiraRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.service.MetaFinanceiraService;
import com.consumoesperto.service.OrcamentoService;
import com.consumoesperto.service.SaldoService;
import com.consumoesperto.util.AppTimeZone;
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

@Service
@RequiredArgsConstructor
public class MotorFinanceiroColetaService {

    private static final int MESES_HISTORICO = 6;

    private final SaldoService saldoService;
    private final TransacaoRepository transacaoRepository;
    private final FaturaRepository faturaRepository;
    private final CartaoCreditoRepository cartaoCreditoRepository;
    private final MetaFinanceiraRepository metaFinanceiraRepository;
    private final MetaFinanceiraService metaFinanceiraService;
    private final OrcamentoService orcamentoService;

    @Transactional(readOnly = true)
    public MotorFinanceiroSnapshot coletar(Long usuarioId) {
        SaldoService.ProjecaoMesCaixa proj = saldoService.calcularProjecaoMes(usuarioId);
        BigDecimal renda = metaFinanceiraService.calcularRendaMensalMediaUltimosTresMeses(usuarioId)
            .orElse(proj.rendaLiquida() != null ? proj.rendaLiquida() : BigDecimal.ZERO);

        List<BigDecimal> despesas6 = serieMensal(usuarioId, Transacao.TipoTransacao.DESPESA);
        List<BigDecimal> receitas6 = serieMensal(usuarioId, Transacao.TipoTransacao.RECEITA);

        YearMonth ym = YearMonth.now();
        List<OrcamentoDTO> orcamentos = orcamentoService.listar(
            usuarioId, ym.getMonthValue(), ym.getYear());
        int totalOrc = orcamentos.size();
        int verde = (int) orcamentos.stream()
            .filter(o -> "VERDE".equalsIgnoreCase(o.getStatus())).count();
        int estourados = (int) orcamentos.stream()
            .filter(o -> o.getPercentualUso() != null
                && o.getPercentualUso().compareTo(BigDecimal.valueOf(100)) >= 0).count();

        BigDecimal limiteTotal = cartaoCreditoRepository.findByUsuarioId(usuarioId).stream()
            .map(CartaoCredito::getLimite)
            .filter(l -> l != null && l.compareTo(BigDecimal.ZERO) > 0)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal faturasPendentes = nz(faturaRepository.sumValorFaturasPendentesByUsuarioId(usuarioId));
        BigDecimal utilizacao = limiteTotal.compareTo(BigDecimal.ZERO) > 0
            ? faturasPendentes.multiply(BigDecimal.valueOf(100))
                .divide(limiteTotal, 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        BigDecimal gastoMedio = media(despesas6);
        BigDecimal mesesReserva = gastoMedio.compareTo(BigDecimal.ZERO) > 0
            ? nz(proj.patrimonioLiquido()).divide(gastoMedio, 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        List<MetaFinanceira> metas = metaFinanceiraRepository
            .findByUsuarioIdOrderByPrioridadeDescDataCriacaoDesc(usuarioId);
        BigDecimal comprometimento = metas.stream()
            .map(MetaFinanceira::getPercentualComprometimento)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal progressoMedio = BigDecimal.ZERO;
        if (!metas.isEmpty()) {
            progressoMedio = metas.stream()
                .map(m -> progressoMeta(m))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(metas.size()), 2, RoundingMode.HALF_UP);
        }

        LocalDateTime ini6 = YearMonth.now().minusMonths(MESES_HISTORICO - 1L).atDay(1).atStartOfDay();
        LocalDateTime fim = AppTimeZone.agora();
        int parceladas = (int) transacaoRepository.findByUsuarioIdAndTipoAndPeriodo(
            usuarioId, Transacao.TipoTransacao.DESPESA, ini6, fim).stream()
            .filter(t -> t.getTotalParcelas() != null && t.getTotalParcelas() > 1).count();
        int comprasForaOrcamento = contarComprasForaOrcamento6m(usuarioId);

        return new MotorFinanceiroSnapshot(
            usuarioId,
            nz(proj.patrimonioLiquido()),
            nz(saldoService.saldoLiquidezImediata(usuarioId)),
            renda,
            nz(proj.saldoProjetadoFimMes()),
            nz(proj.gastoProjetado()),
            nz(proj.receitasPrevistasConsolidadas()),
            nz(proj.despesasPrevistas()),
            faturasPendentes,
            limiteTotal,
            utilizacao,
            mesesReserva,
            despesas6,
            receitas6,
            totalOrc,
            verde,
            estourados,
            parceladas,
            comprasForaOrcamento,
            comprometimento,
            progressoMedio,
            metas.stream().map(this::toMetaSnapshot).toList()
        );
    }

    @Transactional(readOnly = true)
    public BigDecimal gastoLazerMedioMensal(Long usuarioId) {
        LocalDateTime ini = YearMonth.now().minusMonths(MESES_HISTORICO - 1L).atDay(1).atStartOfDay();
        LocalDateTime fim = AppTimeZone.agora();
        List<Object[]> rows = transacaoRepository.findByUsuarioIdAndPeriodoGroupByCategoria(
            usuarioId, ini, fim);
        BigDecimal totalLazer = BigDecimal.ZERO;
        for (Object[] r : rows) {
            String cat = String.valueOf(r[0]).toLowerCase();
            if (cat.contains("lazer") || cat.contains("entreten") || cat.contains("restaur")) {
                totalLazer = totalLazer.add(new BigDecimal(String.valueOf(r[1])));
            }
        }
        return totalLazer.divide(BigDecimal.valueOf(MESES_HISTORICO), 2, RoundingMode.HALF_UP);
    }

    private int contarComprasForaOrcamento6m(Long usuarioId) {
        int total = 0;
        YearMonth ym = YearMonth.now().minusMonths(MESES_HISTORICO - 1L);
        YearMonth fim = YearMonth.now();
        while (!ym.isAfter(fim)) {
            List<OrcamentoDTO> orcs = orcamentoService.listar(
                usuarioId, ym.getMonthValue(), ym.getYear());
            total += (int) orcs.stream()
                .filter(o -> o.getPercentualUso() != null
                    && o.getPercentualUso().compareTo(BigDecimal.valueOf(100)) >= 0)
                .count();
            ym = ym.plusMonths(1);
        }
        return total;
    }

    private MotorFinanceiroSnapshot.MetaSnapshot toMetaSnapshot(MetaFinanceira m) {
        LocalDate criacao = m.getDataCriacao() != null
            ? m.getDataCriacao().toLocalDate() : LocalDate.now();
        LocalDate alvo = m.getDataExpiracao();
        if (alvo == null && m.getPrazoMeses() != null) {
            alvo = criacao.plusMonths(m.getPrazoMeses().longValue());
        }
        if (alvo == null) {
            alvo = criacao.plusMonths(12);
        }
        return new MotorFinanceiroSnapshot.MetaSnapshot(
            m.getId(),
            m.getDescricao(),
            nz(m.getValorTotal()),
            nz(m.getValorAcumulado()),
            alvo,
            criacao,
            nz(m.getPercentualComprometimento())
        );
    }

    private BigDecimal progressoMeta(MetaFinanceira m) {
        if (m.getValorTotal() == null || m.getValorTotal().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return nz(m.getValorAcumulado()).multiply(BigDecimal.valueOf(100))
            .divide(m.getValorTotal(), 2, RoundingMode.HALF_UP);
    }

    private List<BigDecimal> serieMensal(Long usuarioId, Transacao.TipoTransacao tipo) {
        List<BigDecimal> out = new ArrayList<>();
        YearMonth ym = YearMonth.now().minusMonths(MESES_HISTORICO - 1L);
        YearMonth fim = YearMonth.now();
        while (!ym.isAfter(fim)) {
            LocalDateTime ini = ym.atDay(1).atStartOfDay();
            LocalDateTime end = ym.atEndOfMonth().atTime(23, 59, 59);
            BigDecimal v = transacaoRepository.sumConfirmadaByUsuarioIdAndTipoAndPeriodo(
                usuarioId, tipo, ini, end);
            out.add(nz(v));
            ym = ym.plusMonths(1);
        }
        return out;
    }

    private static BigDecimal media(List<BigDecimal> vals) {
        if (vals == null || vals.isEmpty()) return BigDecimal.ZERO;
        return vals.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(vals.size()), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }
}
