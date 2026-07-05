package com.consumoesperto.service;

import com.consumoesperto.config.SaldoReparoProperties;
import com.consumoesperto.dto.DivergenciaSaldoDTO;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.Fatura;
import com.consumoesperto.model.MovimentacaoSaldoLog.OrigemMovimentacaoSaldo;
import com.consumoesperto.repository.ContaBancariaRepository;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reparo dos dados corrompidos pelos bugs já corrigidos na lógica:
 * (a) exclusão em lote sem estorno → saldo inflado;
 * (b) reparo antigo que apagou o saldo_inicial → abertura perdida;
 * (c) fatura PAGA com valorPago preenchido sem PAGAMENTO_FATURA real.
 *
 * Não destrutivo por padrão: dry-run sempre; aplicação exige
 * {@code consumoesperto.saldo.reparo.enabled=true} + confirmação de backup + flag no pedido.
 * Recomputa usando a MESMA fórmula da fonte da verdade
 * ({@link SaldoService#calcularSaldoEsperadoPorMovimentos}) — nenhuma fórmula nova.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SaldoReparoService {

    private static final BigDecimal TOLERANCIA = new BigDecimal("0.02");

    private final SaldoReparoProperties properties;
    private final SaldoService saldoService;
    private final SaldoIntegridadeService saldoIntegridadeService;
    private final SaldoMovimentacaoService saldoMovimentacaoService;
    private final ContaBancariaRepository contaBancariaRepository;
    private final FaturaRepository faturaRepository;
    private final TransacaoRepository transacaoRepository;

    public record ReparoContaResultado(
        Long contaId,
        String nomeConta,
        BigDecimal saldoPersistido,
        BigDecimal saldoCalculado,
        BigDecimal delta,
        BigDecimal saldoInicialAtual,
        BigDecimal saldoInicialNovo,
        boolean aplicado,
        String motivo
    ) {}

    public record ReparoFaturaResultado(
        Long faturaId,
        String numeroFatura,
        BigDecimal valorPagoAtual,
        BigDecimal pagamentosReais,
        boolean aplicado,
        String motivo
    ) {}

    public record RelatorioReparo(
        List<DivergenciaSaldoDTO> divergenciasSaldo,
        List<ReparoFaturaResultado> faturasComValorPagoSemCaixa
    ) {}

    /** 1.1 — Relatório read-only: contas divergentes + faturas PAGA sem pagamento real. */
    @Transactional(readOnly = true)
    public RelatorioReparo relatorio(Long usuarioId) {
        List<DivergenciaSaldoDTO> divergencias = saldoIntegridadeService.auditarUsuario(usuarioId);
        List<ReparoFaturaResultado> faturas = listarFaturasComValorPagoSemCaixa(usuarioId, false, "dry-run");
        return new RelatorioReparo(divergencias, faturas);
    }

    /**
     * 1.2 — Reparo pontual de uma conta. {@code saldoInicialCorreto} é opcional e cobre o caso (b):
     * quando a abertura foi apagada, o humano informa o valor certo e o saldo é recomputado.
     */
    @Transactional
    public ReparoContaResultado repararConta(
        Long contaId,
        Long usuarioId,
        boolean confirmar,
        boolean backupConfirmado,
        BigDecimal saldoInicialCorreto
    ) {
        ContaBancaria conta = contaBancariaRepository.findByIdAndUsuarioId(contaId, usuarioId)
            .orElseThrow(() -> new IllegalArgumentException("Conta não encontrada para este usuário."));

        BigDecimal saldoInicialAtual = nz(conta.getSaldoInicial());
        BigDecimal saldoInicialNovo = saldoInicialCorreto != null
            ? saldoInicialCorreto.setScale(2, RoundingMode.HALF_UP)
            : saldoInicialAtual;

        String recusa = validarPreCondicoes(confirmar, backupConfirmado);

        if (recusa != null) {
            // Dry-run: mostra o que mudaria, sem escrever nada
            BigDecimal calculadoDry = calcularComAberturaHipotetica(conta, usuarioId, saldoInicialNovo);
            return new ReparoContaResultado(
                contaId, conta.getNome(), nz(conta.getSaldoAtual()), calculadoDry,
                calculadoDry.subtract(nz(conta.getSaldoAtual())),
                saldoInicialAtual, saldoInicialNovo, false, recusa
            );
        }

        if (saldoInicialCorreto != null
            && saldoInicialAtual.compareTo(saldoInicialNovo) != 0) {
            conta.setSaldoInicial(saldoInicialNovo);
            contaBancariaRepository.save(conta);
            log.warn("[REPARO-SALDO] contaId={} saldo_inicial {} → {} (informado pelo operador)",
                contaId, saldoInicialAtual, saldoInicialNovo);
        }

        BigDecimal saldoAntes = nz(conta.getSaldoAtual());
        BigDecimal calculado = saldoService.calcularSaldoEsperadoPorMovimentos(contaId, usuarioId);
        SaldoMovimentacaoContexto.definirOrigem(OrigemMovimentacaoSaldo.REPARO);
        try {
            if (saldoAntes.subtract(calculado).abs().compareTo(TOLERANCIA) > 0) {
                saldoMovimentacaoService.definirSaldoReconciliado(contaId, calculado);
                log.warn("[REPARO-SALDO] contaId={} saldo {} → {} (fórmula abertura+movimentos)",
                    contaId, saldoAntes, calculado);
            }
        } finally {
            SaldoMovimentacaoContexto.limpar();
        }
        // Idempotente: segunda execução encontra delta zero e não escreve nada
        return new ReparoContaResultado(
            contaId, conta.getNome(), saldoAntes, calculado, calculado.subtract(saldoAntes),
            saldoInicialAtual, saldoInicialNovo, true, "aplicado"
        );
    }

    /** 1.2(c) — Fatura PAGA com valorPago sem caixa: reseta para a soma real de PAGAMENTO_FATURA. */
    @Transactional
    public List<ReparoFaturaResultado> repararFaturasValorPago(
        Long usuarioId,
        boolean confirmar,
        boolean backupConfirmado
    ) {
        String recusa = validarPreCondicoes(confirmar, backupConfirmado);
        if (recusa != null) {
            return listarFaturasComValorPagoSemCaixa(usuarioId, false, recusa);
        }
        return listarFaturasComValorPagoSemCaixa(usuarioId, true, "aplicado");
    }

    private List<ReparoFaturaResultado> listarFaturasComValorPagoSemCaixa(Long usuarioId, boolean aplicar, String motivo) {
        List<ReparoFaturaResultado> out = new ArrayList<>();
        for (Fatura f : faturaRepository.findByCartaoCreditoUsuarioId(usuarioId)) {
            if (f.getStatusFatura() != Fatura.StatusFatura.PAGA || f.getId() == null) {
                continue;
            }
            BigDecimal valorPago = nz(f.getValorPago());
            BigDecimal reais = nz(transacaoRepository.sumPagamentoFaturaConfirmadoPorFaturaId(f.getId()));
            if (valorPago.subtract(reais).abs().compareTo(TOLERANCIA) <= 0) {
                continue;
            }
            if (aplicar) {
                f.setValorPago(reais);
                faturaRepository.save(f);
                log.warn("[REPARO-FATURA] faturaId={} valorPago {} → {} (soma PAGAMENTO_FATURA real; status mantido)",
                    f.getId(), valorPago, reais);
            }
            out.add(new ReparoFaturaResultado(f.getId(), f.getNumeroFatura(), valorPago, reais, aplicar, motivo));
        }
        return out;
    }

    /** Fail-closed: sem flag do servidor + confirmação + backup, o reparo recusa e devolve dry-run. */
    private String validarPreCondicoes(boolean confirmar, boolean backupConfirmado) {
        if (!properties.isEnabled()) {
            return "recusado: consumoesperto.saldo.reparo.enabled=false (dry-run)";
        }
        if (!confirmar) {
            return "dry-run: envie confirmar=true para aplicar";
        }
        if (!backupConfirmado) {
            return "recusado: confirme o backup (backupConfirmado=true) antes de aplicar";
        }
        return null;
    }

    /** Saldo esperado se a abertura fosse o valor informado — só para exibir no dry-run. */
    private BigDecimal calcularComAberturaHipotetica(ContaBancaria conta, Long usuarioId, BigDecimal aberturaHipotetica) {
        BigDecimal calculadoComAberturaAtual = saldoService.calcularSaldoEsperadoPorMovimentos(conta.getId(), usuarioId);
        BigDecimal aberturaAtual = nz(conta.getSaldoInicial());
        if (Objects.equals(aberturaAtual, aberturaHipotetica)) {
            return calculadoComAberturaAtual;
        }
        return calculadoComAberturaAtual.subtract(aberturaAtual).add(nz(aberturaHipotetica))
            .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
