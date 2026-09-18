package com.consumoesperto.service;

import com.consumoesperto.config.ForecastProjecaoConfig;
import com.consumoesperto.model.Fatura;
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
import java.util.List;

/**
 * Componentes bottom-up compartilhados entre projeção de safra ({@link SaldoService})
 * e sentinela ({@link PrevisaoFluxoCaixaService}) — evita fórmulas divergentes.
 *
 * <p>Fontes de saída de caixa (disjuntas): despesas fixas restantes (não realizadas),
 * restante de faturas do mês (parcelas de cartão já estão na fatura), parcelas de empréstimo
 * com {@code emprestimoId} que debitam conta, e variável Anti-Susto.
 * Assinaturas e agendamentos não entram no número — o cadastro de despesa fixa é a
 * obrigação económica; listá-los na UI não duplica a projeção.
 */
@Service
@RequiredArgsConstructor
public class ComposicaoProjecaoMesService {

    private final DespesaFixaService despesaFixaService;
    private final TransacaoRepository transacaoRepository;
    private final FaturaRepository faturaRepository;
    private final ForecastProjecaoConfig forecastProjecaoConfig;

    /**
     * Componentes disjuntos da obrigação do mês (R1) — mesmos números da composição, sem Anti-Susto.
     * {@code parcelasEmprestimoCaixa} exclui desconto em folha (sai de conta);
     * {@code parcelasEmprestimoTodas} inclui consignado em folha (exibição da parcela do mês).
     */
    public record PartesObrigacoesMes(
        BigDecimal fixas,
        BigDecimal faturas,
        BigDecimal parcelasEmprestimoCaixa,
        BigDecimal parcelasEmprestimoFolha,
        BigDecimal parcelasEmprestimoTodas
    ) {
        public BigDecimal comprometidoDisjuntoCaixa() {
            return nz(fixas).add(nz(faturas)).add(nz(parcelasEmprestimoCaixa));
        }

        public BigDecimal comprometidoDisjuntoExibicao() {
            return nz(fixas).add(nz(faturas)).add(nz(parcelasEmprestimoTodas));
        }
    }

    @Transactional(readOnly = true)
    public PartesObrigacoesMes partesObrigacoesMes(Long usuarioId, YearMonth ym, LocalDate referencia) {
        LocalDateTime inicio = ym.atDay(1).atStartOfDay();
        LocalDateTime fimMes = ym.atEndOfMonth().atTime(23, 59, 59);
        BigDecimal fixas = nz(despesaFixaService.somarValorRestanteNoMes(usuarioId, referencia));
        BigDecimal faturas = somarFaturasRestantesNoMes(usuarioId, inicio, fimMes);
        BigDecimal parcelasCaixa = nz(transacaoRepository.sumParcelasEmprestimoPrevistasNoMes(
            usuarioId, inicio, fimMes));
        BigDecimal parcelasTodas = nz(transacaoRepository.sumParcelasEmprestimoPrevistasNoMesTodas(
            usuarioId, inicio, fimMes));
        BigDecimal parcelasFolha = parcelasTodas.subtract(parcelasCaixa).max(BigDecimal.ZERO);
        return new PartesObrigacoesMes(fixas, faturas, parcelasCaixa, parcelasFolha, parcelasTodas);
    }

    /**
     * Despesas futuras/restantes no mês: fixas ainda não lançadas + restante de faturas
     * + parcelas de empréstimo que debitam conta + estimativa variável (Anti-Susto).
     * Consignado em folha não entra (a query de parcelas de caixa já exclui {@code descontoEmFolha=true}).
     * Compra parcelada no cartão ({@code grupoParcelaId}) não entra — já está na fatura.
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
        BigDecimal faturas = somarFaturasRestantesNoMes(usuarioId, inicio, fimMes);
        BigDecimal parcelasEmprestimo = nz(transacaoRepository.sumParcelasEmprestimoPrevistasNoMes(
            usuarioId, inicio, fimMes));
        BigDecimal obrigacoes = fixas.add(faturas).add(parcelasEmprestimo);

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
        int diasRestantes = Math.max(0, diasNoMes - diaAtual);
        BigDecimal variavelRestante = nz(mediaDiaria)
            .multiply(BigDecimal.valueOf(diasRestantes))
            .setScale(2, RoundingMode.HALF_UP);
        return obrigacoes.add(variavelRestante).setScale(2, RoundingMode.HALF_UP);
    }

    /** Restante de caixa das faturas do mês — pagamento parcial reduz; parcela de cartão não soma à parte. */
    private BigDecimal somarFaturasRestantesNoMes(Long usuarioId, LocalDateTime inicio, LocalDateTime fimMes) {
        List<Fatura> lista = faturaRepository.findProximasNaoPagas(usuarioId, inicio, fimMes);
        if (lista == null || lista.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return lista.stream()
            .map(Fatura::valorRestanteCaixa)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
