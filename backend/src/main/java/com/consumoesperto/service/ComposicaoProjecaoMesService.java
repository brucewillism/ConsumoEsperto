package com.consumoesperto.service;

import com.consumoesperto.config.ForecastProjecaoConfig;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

/**
 * Componentes bottom-up compartilhados entre projeção de safra ({@link SaldoService})
 * e sentinela ({@link PrevisaoFluxoCaixaService}) — evita fórmulas divergentes.
 */
@Service
@RequiredArgsConstructor
public class ComposicaoProjecaoMesService {

    private final DespesaFixaService despesaFixaService;
    private final TransacaoRepository transacaoRepository;
    private final FaturaRepository faturaRepository;
    private final ForecastProjecaoConfig forecastProjecaoConfig;

    /**
     * Despesas previstas restantes no mês: fixas + faturas + parcelas de empréstimo + gasto variável (Anti-Susto).
     */
    @Transactional(readOnly = true)
    public BigDecimal comporDespesasPrevistasMes(
        Long usuarioId,
        YearMonth ym,
        LocalDate referencia,
        BigDecimal mediaDiaria
    ) {
        LocalDateTime inicio = ym.atDay(1).atStartOfDay();
        LocalDateTime fimMes = ym.atEndOfMonth().atTime(23, 59, 59);
        int diasNoMes = ym.lengthOfMonth();
        int diaAtual = referencia.getDayOfMonth();

        BigDecimal fixas = nz(despesaFixaService.somarValorRestanteNoMes(usuarioId, referencia));
        BigDecimal faturas = nz(faturaRepository.sumValorFaturasPendentesNoMes(usuarioId, inicio, fimMes));
        BigDecimal parcelasEmprestimo = nz(transacaoRepository.sumParcelasEmprestimoPrevistasNoMes(
            usuarioId, inicio, fimMes));

        if (ProjecaoMesCaixaSupport.usarModoAntiSusto(diaAtual, forecastProjecaoConfig.getDiaLiminarAntiSusto())) {
            return ProjecaoMesCaixaSupport.calcularDespesasPrevistasAntiSusto(
                mediaDiaria,
                diaAtual,
                diasNoMes,
                fixas.add(faturas),
                parcelasEmprestimo,
                forecastProjecaoConfig.getMargemVariavelPct()
            );
        }
        BigDecimal gastoProjetado = nz(mediaDiaria)
            .multiply(BigDecimal.valueOf(diasNoMes))
            .setScale(2, RoundingMode.HALF_UP);
        return gastoProjetado.subtract(
            nz(mediaDiaria).multiply(BigDecimal.valueOf(diaAtual)).setScale(2, RoundingMode.HALF_UP)
        ).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
