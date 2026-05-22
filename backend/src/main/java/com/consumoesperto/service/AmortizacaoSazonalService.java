package com.consumoesperto.service;

import com.consumoesperto.dto.ParcelaReceitaFiscalDTO;
import com.consumoesperto.dto.PlanejamentoFiscalResumoDTO;
import com.consumoesperto.model.CompraParcelada;
import com.consumoesperto.repository.CompraParceladaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Debt Snowball sazonal — cruza parcelamentos ativos com picos de caixa (13º/IR).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AmortizacaoSazonalService {

    private static final int SEMANAS_ANTECEDENCIA_ALERTA = 3;

    @Value("${consumoesperto.amortizacao.taxa-juros-mensal-padrao:0.0199}")
    private BigDecimal taxaJurosMensalPadrao;

    private final CompraParceladaRepository compraParceladaRepository;
    private final PlanejamentoFiscalService planejamentoFiscalService;

    public record SimulacaoAntecipacao(
        Long compraParceladaId,
        String descricao,
        BigDecimal parcelasRestantesValor,
        int parcelasRestantes,
        LocalDate dataProximaReceitaFiscal,
        String rotuloReceitaFiscal,
        BigDecimal valorReceitaFiscal,
        BigDecimal jurosEconomizadosEstimados,
        BigDecimal valorSugeridoAmortizar
    ) {}

    @Transactional(readOnly = true)
    public List<SimulacaoAntecipacao> simularOportunidades(Long usuarioId) {
        List<CompraParcelada> ativas = compraParceladaRepository.findActiveInstallmentsByUsuarioId(usuarioId);
        if (ativas.isEmpty()) {
            return List.of();
        }
        PlanejamentoFiscalResumoDTO fiscal = planejamentoFiscalService.simular(usuarioId);
        List<ParcelaReceitaFiscalDTO> parcelasFiscais = fiscal.getParcelas() != null
            ? fiscal.getParcelas() : List.of();
        if (parcelasFiscais.isEmpty()) {
            return List.of();
        }

        LocalDate hoje = LocalDate.now();
        int ano = hoje.getYear();
        List<SimulacaoAntecipacao> out = new ArrayList<>();

        for (CompraParcelada cp : ativas) {
            int restantes = cp.getNumeroParcelas() - (cp.getParcelaAtual() != null ? cp.getParcelaAtual() : 0);
            if (restantes <= 0) {
                continue;
            }
            BigDecimal valorRestante = cp.getValorParcela().multiply(BigDecimal.valueOf(restantes))
                .setScale(2, RoundingMode.HALF_UP);

            ParcelaReceitaFiscalDTO melhorParcela = parcelasFiscais.stream()
                .filter(p -> p.getValor() != null && p.getValor().compareTo(BigDecimal.ZERO) > 0)
                .min(Comparator.comparingInt(p -> diasAteParcela(hoje, ano, p)))
                .orElse(null);
            if (melhorParcela == null) {
                continue;
            }
            int dias = diasAteParcela(hoje, ano, melhorParcela);
            if (dias < 0 || dias > 120) {
                continue;
            }

            BigDecimal jurosEst = calcularJurosEconomizados(valorRestante, restantes);
            BigDecimal sugerido = valorRestante.min(melhorParcela.getValor()).setScale(2, RoundingMode.HALF_UP);

            LocalDate dataReceita = LocalDate.of(ano, melhorParcela.getMes(), melhorParcela.getDia());
            out.add(new SimulacaoAntecipacao(
                cp.getId(),
                cp.getDescricao(),
                valorRestante,
                restantes,
                dataReceita,
                melhorParcela.getRotulo(),
                melhorParcela.getValor(),
                jurosEst,
                sugerido
            ));
        }
        return out.stream()
            .sorted(Comparator.comparing(SimulacaoAntecipacao::jurosEconomizadosEstimados).reversed())
            .toList();
    }

    /** Oportunidades dentro da janela de alerta proativo (semanas antes da receita fiscal). */
    @Transactional(readOnly = true)
    public List<SimulacaoAntecipacao> oportunidadesParaAlertaProativo(Long usuarioId) {
        LocalDate hoje = LocalDate.now();
        int janelaDias = SEMANAS_ANTECEDENCIA_ALERTA * 7;
        return simularOportunidades(usuarioId).stream()
            .filter(s -> {
                if (s.dataProximaReceitaFiscal() == null) {
                    return false;
                }
                long dias = ChronoUnit.DAYS.between(hoje, s.dataProximaReceitaFiscal());
                return dias >= 0 && dias <= janelaDias && s.jurosEconomizadosEstimados().compareTo(BigDecimal.TEN) > 0;
            })
            .toList();
    }

    private BigDecimal calcularJurosEconomizados(BigDecimal valorRestante, int parcelasRestantes) {
        if (valorRestante.compareTo(BigDecimal.ZERO) <= 0 || parcelasRestantes <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal taxa = taxaJurosMensalPadrao != null ? taxaJurosMensalPadrao : new BigDecimal("0.0199");
        BigDecimal fator = taxa.multiply(BigDecimal.valueOf(parcelasRestantes))
            .multiply(new BigDecimal("0.5"));
        return valorRestante.multiply(fator).setScale(2, RoundingMode.HALF_UP);
    }

    private static int diasAteParcela(LocalDate hoje, int ano, ParcelaReceitaFiscalDTO p) {
        if (p.getMes() <= 0 || p.getDia() <= 0) {
            return Integer.MAX_VALUE;
        }
        try {
            return (int) ChronoUnit.DAYS.between(hoje, LocalDate.of(ano, p.getMes(), p.getDia()));
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }
}
