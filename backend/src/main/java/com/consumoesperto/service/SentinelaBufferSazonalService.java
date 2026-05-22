package com.consumoesperto.service;

import com.consumoesperto.dto.ParcelaReceitaFiscalDTO;
import com.consumoesperto.dto.PlanejamentoFiscalResumoDTO;
import com.consumoesperto.model.ContaBancaria;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Colchão virtual do Sentinela — flexibiliza alertas quando há receita fiscal (13º/IR) nos próximos 60 dias.
 * Lê patrimônio dinamicamente das contas bancárias via {@link SaldoService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SentinelaBufferSazonalService {

    private static final int JANELA_DIAS = 60;
    private static final BigDecimal FRACAO_COLCHAO = new BigDecimal("0.50");

    private final PlanejamentoFiscalService planejamentoFiscalService;
    private final SaldoService saldoService;
    private final ContaBancariaService contaBancariaService;

    public record ColchaoSazonal(
        BigDecimal valorTotal,
        int diasAteProximaReceita,
        String descricaoProxima
    ) {}

    public record ColchaoComPatrimonio(
        ColchaoSazonal colchao,
        BigDecimal patrimonioLiquido,
        BigDecimal saldoContaReferencia
    ) {}

    @Transactional(readOnly = true)
    public ColchaoSazonal calcularColchao(Long usuarioId) {
        return calcularColchaoInterno(usuarioId).colchao();
    }

    /**
     * Recalcula colchão sazonal lendo saldos atualizados das contas (pós-crédito de renda).
     */
    @Transactional(readOnly = true)
    public ColchaoSazonal recalcularColchao(Long usuarioId, Long contaReferenciaId) {
        ColchaoComPatrimonio ctx = calcularColchaoInterno(usuarioId, contaReferenciaId);
        log.debug("[SENTINELA] Recálculo colchão userId={} patrimonio={} saldoConta={} colchao={}",
            usuarioId, ctx.patrimonioLiquido(), ctx.saldoContaReferencia(), ctx.colchao().valorTotal());
        return ctx.colchao();
    }

    private ColchaoComPatrimonio calcularColchaoInterno(Long usuarioId) {
        return calcularColchaoInterno(usuarioId, null);
    }

    private ColchaoComPatrimonio calcularColchaoInterno(Long usuarioId, Long contaReferenciaId) {
        BigDecimal patrimonio = saldoService.patrimonioLiquido(usuarioId);
        BigDecimal saldoConta = resolverSaldoConta(usuarioId, contaReferenciaId, patrimonio);

        if (usuarioId == null) {
            return new ColchaoComPatrimonio(
                new ColchaoSazonal(BigDecimal.ZERO, -1, null), patrimonio, saldoConta);
        }

        PlanejamentoFiscalResumoDTO resumo = planejamentoFiscalService.simular(usuarioId);
        if (resumo.getParcelas() == null || resumo.getParcelas().isEmpty()) {
            return new ColchaoComPatrimonio(
                new ColchaoSazonal(BigDecimal.ZERO, -1, null), patrimonio, saldoConta);
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

        ColchaoSazonal resultado;
        if (menorDias == Integer.MAX_VALUE) {
            resultado = new ColchaoSazonal(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), -1, null);
        } else {
            resultado = new ColchaoSazonal(
                colchao.setScale(2, RoundingMode.HALF_UP),
                menorDias,
                descProxima
            );
        }
        return new ColchaoComPatrimonio(resultado, patrimonio, saldoConta);
    }

    private BigDecimal resolverSaldoConta(Long usuarioId, Long contaReferenciaId, BigDecimal patrimonioFallback) {
        if (contaReferenciaId == null) {
            return patrimonioFallback;
        }
        try {
            ContaBancaria conta = contaBancariaService.buscarEntidade(contaReferenciaId, usuarioId);
            return conta.getSaldoAtual() != null
                ? conta.getSaldoAtual().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        } catch (Exception e) {
            return patrimonioFallback;
        }
    }
}
