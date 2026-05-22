package com.consumoesperto.service;

import com.consumoesperto.dto.ParcelaReceitaFiscalDTO;
import com.consumoesperto.dto.PlanejamentoFiscalResumoDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Colchão virtual do Sentinela — flexibiliza alertas quando há receita fiscal (13º/IR) nos próximos 60 dias.
 */
@Service
@RequiredArgsConstructor
public class SentinelaBufferSazonalService {

    private static final int JANELA_DIAS = 60;
    private static final BigDecimal FRACAO_COLCHAO = new BigDecimal("0.50");

    private final PlanejamentoFiscalService planejamentoFiscalService;

    public record ColchaoSazonal(
        BigDecimal valorTotal,
        int diasAteProximaReceita,
        String descricaoProxima
    ) {}

    @Transactional(readOnly = true)
    public ColchaoSazonal calcularColchao(Long usuarioId) {
        if (usuarioId == null) {
            return new ColchaoSazonal(BigDecimal.ZERO, -1, null);
        }
        PlanejamentoFiscalResumoDTO resumo = planejamentoFiscalService.simular(usuarioId);
        if (resumo.getParcelas() == null || resumo.getParcelas().isEmpty()) {
            return new ColchaoSazonal(BigDecimal.ZERO, -1, null);
        }

        LocalDate hoje = LocalDate.now();
        int ano = hoje.getYear();
        BigDecimal colchao = BigDecimal.ZERO;
        int menorDias = Integer.MAX_VALUE;
        String descProxima = null;

        for (ParcelaReceitaFiscalDTO parcela : resumo.getParcelas()) {
            if (parcela.getValor() == null || parcela.getValor().compareTo(BigDecimal.ZERO) <= 0
                || parcela.getMes() <= 0 || parcela.getDia() <= 0) {
                continue;
            }
            LocalDate dataParcela;
            try {
                dataParcela = LocalDate.of(ano, parcela.getMes(), parcela.getDia());
            } catch (Exception e) {
                continue;
            }
            if (dataParcela.isBefore(hoje)) {
                continue;
            }
            long dias = ChronoUnit.DAYS.between(hoje, dataParcela);
            if (dias > JANELA_DIAS) {
                continue;
            }
            colchao = colchao.add(parcela.getValor().multiply(FRACAO_COLCHAO));
            if (dias < menorDias) {
                menorDias = (int) dias;
                descProxima = parcela.getRotulo();
            }
        }

        if (menorDias == Integer.MAX_VALUE) {
            return new ColchaoSazonal(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), -1, null);
        }
        return new ColchaoSazonal(
            colchao.setScale(2, RoundingMode.HALF_UP),
            menorDias,
            descProxima
        );
    }
}
