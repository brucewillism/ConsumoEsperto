package com.consumoesperto.service;

import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.MovimentacaoSaldoLog;
import com.consumoesperto.model.MovimentacaoSaldoLog.TipoOperacaoSaldo;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.ContaBancariaRepository;
import com.consumoesperto.repository.MovimentacaoSaldoLogRepository;
import com.consumoesperto.util.AppTimeZone;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Atualiza {@link ContaBancaria#getSaldoAtual()} conforme o ciclo de vida das transações confirmadas.
 * Fonte única de verdade para {@link #impactaSaldo(Transacao)} e {@link #deltaSaldo(Transacao)}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SaldoMovimentacaoService {

    private static final int SCALE = 2;
    private static final ZoneId ZONA_BR = ZoneId.of("America/Sao_Paulo");

    private final ContaBancariaRepository contaBancariaRepository;
    private final MovimentacaoSaldoLogRepository movimentacaoSaldoLogRepository;

    /** Snapshot imutável para estorno/recálculo em edições. */
    public record MovimentacaoSnapshot(
        Long contaBancariaId,
        BigDecimal valor,
        Transacao.TipoTransacao tipoTransacao,
        Transacao.StatusConferencia statusConferencia,
        Long faturaId,
        LocalDate dataTransacao
    ) {
        static MovimentacaoSnapshot from(Transacao t) {
            if (t == null) {
                return null;
            }
            LocalDate data = t.getDataTransacao() != null ? t.getDataTransacao().toLocalDate() : null;
            return new MovimentacaoSnapshot(
                t.getContaBancaria() != null ? t.getContaBancaria().getId() : null,
                t.getValor(),
                t.getTipoTransacao(),
                t.getStatusConferencia(),
                t.getFatura() != null ? t.getFatura().getId() : null,
                data
            );
        }
    }

    public MovimentacaoSnapshot capturarSnapshot(Transacao transacao) {
        return MovimentacaoSnapshot.from(transacao);
    }

    /**
     * Uma transação só impacta o saldo se todas as condições forem verdadeiras:
     * conta vinculada, confirmada, data não-futura, e tipo elegível (não despesa de cartão na fatura).
     */
    public boolean impactaSaldo(Transacao transacao) {
        return impactaSaldo(MovimentacaoSnapshot.from(transacao));
    }

    public boolean impactaSaldo(MovimentacaoSnapshot snap) {
        if (snap == null || snap.contaBancariaId() == null) {
            return false;
        }
        if (snap.statusConferencia() != Transacao.StatusConferencia.CONFIRMADA) {
            return false;
        }
        if (snap.dataTransacao() != null && snap.dataTransacao().isAfter(LocalDate.now(ZONA_BR))) {
            return false;
        }
        if (snap.faturaId() != null
            && snap.tipoTransacao() != Transacao.TipoTransacao.PAGAMENTO_FATURA) {
            return false;
        }
        return snap.tipoTransacao() != null && snap.valor() != null;
    }

    /** Delta monetário (+ receita, − despesa/investimento/pagamento fatura). */
    public BigDecimal deltaSaldo(Transacao transacao) {
        if (!impactaSaldo(transacao)) {
            return BigDecimal.ZERO;
        }
        return deltaSaldo(MovimentacaoSnapshot.from(transacao));
    }

    private BigDecimal deltaSaldo(MovimentacaoSnapshot snap) {
        if (snap == null || snap.tipoTransacao() == null || snap.valor() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal valor = scale(snap.valor());
        return switch (snap.tipoTransacao()) {
            case RECEITA -> valor;
            case DESPESA, INVESTIMENTO, PAGAMENTO_FATURA -> valor.negate();
        };
    }

    @Transactional
    public void aplicarCriacao(Transacao transacao) {
        BigDecimal delta = deltaSaldo(transacao);
        if (delta.compareTo(BigDecimal.ZERO) == 0 || transacao.getContaBancaria() == null) {
            return;
        }
        aplicarDelta(transacao.getContaBancaria().getId(), delta, TipoOperacaoSaldo.CRIACAO, transacao.getId());
    }

    @Transactional
    public void sincronizarMovimentacao(MovimentacaoSnapshot antes, Transacao depois) {
        if (antes != null && impactaSaldo(antes)) {
            BigDecimal impactoAnterior = deltaSaldo(antes);
            if (impactoAnterior.compareTo(BigDecimal.ZERO) != 0 && antes.contaBancariaId() != null) {
                aplicarDelta(antes.contaBancariaId(), impactoAnterior.negate(), TipoOperacaoSaldo.EDICAO,
                    depois != null ? depois.getId() : null);
            }
        }
        aplicarCriacao(depois);
    }

    @Transactional
    public void aplicarExclusao(Transacao transacao) {
        BigDecimal impacto = deltaSaldo(transacao);
        if (impacto.compareTo(BigDecimal.ZERO) == 0 || transacao.getContaBancaria() == null) {
            return;
        }
        aplicarDelta(transacao.getContaBancaria().getId(), impacto.negate(), TipoOperacaoSaldo.EXCLUSAO, transacao.getId());
    }

    /** Crédito direto na conta (ex.: estorno de pagamento de fatura quando o JOIN JPA não carrega a carteira). */
    @Transactional
    public void creditarConta(Long contaBancariaId, BigDecimal valor) {
        if (contaBancariaId == null || valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        aplicarDelta(contaBancariaId, scale(valor), TipoOperacaoSaldo.CREDITO_DIRETO, null);
    }

    /**
     * Transferência interna TED/PIX — patrimônio total inalterado.
     */
    @Transactional
    public void aplicarTransferenciaEntreContas(Long contaOrigemId, Long contaDestinoId, BigDecimal valor) {
        if (contaOrigemId == null || contaDestinoId == null || contaOrigemId.equals(contaDestinoId)) {
            throw new IllegalArgumentException("Contas de origem e destino devem ser distintas.");
        }
        BigDecimal v = scale(valor);
        if (v.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Valor da transferência deve ser positivo.");
        }
        // Trava as duas contas em ordem de id para evitar deadlock entre transferências cruzadas
        Long primeiro = Math.min(contaOrigemId, contaDestinoId);
        Long segundo = Math.max(contaOrigemId, contaDestinoId);
        travarConta(primeiro);
        travarConta(segundo);
        aplicarDelta(contaOrigemId, v.negate(), TipoOperacaoSaldo.TRANSFERENCIA_SAIDA, null);
        aplicarDelta(contaDestinoId, v, TipoOperacaoSaldo.TRANSFERENCIA_ENTRADA, null);
        log.info("[MULTICARTEIRA] Transferência {} → {} valor {}", contaOrigemId, contaDestinoId, v);
    }

    /** Define saldo nominal após reconciliação idempotente (não valida cheque especial). */
    @Transactional
    public BigDecimal definirSaldoReconciliado(Long contaId, BigDecimal saldoCalculado) {
        ContaBancaria conta = travarConta(contaId);
        BigDecimal antes = scale(conta.getSaldoAtual());
        BigDecimal saldo = scale(saldoCalculado);
        conta.setSaldoAtual(saldo);
        contaBancariaRepository.save(conta);
        registrarAuditoria(conta, saldo.subtract(antes), antes, saldo, TipoOperacaoSaldo.RECONCILIACAO, null);
        log.info("[MULTICARTEIRA] Conta {} saldo reconciliado → {}", contaId, saldo);
        return saldo;
    }

    /** Ajuste manual de saldo (WhatsApp/reconciliação) — lock + linha no ledger. */
    @Transactional
    public BigDecimal ajustarSaldoManual(Long contaId, BigDecimal novoSaldo) {
        ContaBancaria conta = travarConta(contaId);
        BigDecimal antes = scale(conta.getSaldoAtual());
        BigDecimal alvo = scale(novoSaldo);
        BigDecimal delta = alvo.subtract(antes);
        if (delta.compareTo(BigDecimal.ZERO) == 0) {
            return alvo;
        }
        conta.setSaldoAtual(alvo);
        contaBancariaRepository.save(conta);
        registrarAuditoria(conta, delta, antes, alvo, TipoOperacaoSaldo.AJUSTE_MANUAL, null);
        log.info("[MULTICARTEIRA] Conta {} ajuste manual → {} (delta {})", contaId, alvo, delta);
        return alvo;
    }

    private void aplicarDelta(Long contaId, BigDecimal delta, TipoOperacaoSaldo tipoOperacao, Long transacaoId) {
        ContaBancaria conta = travarConta(contaId);
        BigDecimal antes = scale(conta.getSaldoAtual());
        BigDecimal saldo = antes.add(scale(delta));
        if (delta.compareTo(BigDecimal.ZERO) < 0) {
            BigDecimal debito = delta.negate();
            if (!conta.temSaldoSuficiente(debito)) {
                throw new IllegalArgumentException(
                    "Saldo insuficiente na conta (incluindo cheque especial). Disponível: R$ "
                        + conta.getSaldoDisponivel().setScale(SCALE, RoundingMode.HALF_UP));
            }
        }
        conta.setSaldoAtual(saldo);
        contaBancariaRepository.save(conta);
        registrarAuditoria(conta, scale(delta), antes, saldo, tipoOperacao, transacaoId);
        log.debug("[MULTICARTEIRA] Conta {} saldo → {} (delta {})", contaId, saldo, delta);
    }

    /** SELECT ... FOR UPDATE — mutações concorrentes na mesma conta serializam aqui. */
    private ContaBancaria travarConta(Long contaId) {
        return contaBancariaRepository.findByIdForUpdate(contaId)
            .orElseThrow(() -> new RuntimeException("Conta bancária não encontrada: " + contaId));
    }

    /** Uma linha append-only por mutação de saldo — nunca atualizada/removida. */
    private void registrarAuditoria(
        ContaBancaria conta,
        BigDecimal delta,
        BigDecimal saldoAntes,
        BigDecimal saldoDepois,
        TipoOperacaoSaldo tipoOperacao,
        Long transacaoId
    ) {
        try {
            MovimentacaoSaldoLog linha = new MovimentacaoSaldoLog();
            linha.setContaId(conta.getId());
            linha.setTransacaoId(transacaoId);
            linha.setUsuarioId(conta.getUsuario() != null ? conta.getUsuario().getId() : null);
            linha.setDelta(delta);
            linha.setSaldoAntes(saldoAntes);
            linha.setSaldoDepois(saldoDepois);
            linha.setOrigem(SaldoMovimentacaoContexto.origemAtual());
            linha.setTipoOperacao(tipoOperacao);
            linha.setCriadoEm(OffsetDateTime.now(AppTimeZone.BR));
            movimentacaoSaldoLogRepository.save(linha);
        } catch (Exception e) {
            // Auditoria nunca pode derrubar a operação financeira; a falha fica visível no log
            log.error("[AUDIT-SALDO] Falha ao gravar trilha contaId={} delta={}: {}",
                conta.getId(), delta, e.getMessage());
        }
    }

    private static BigDecimal scale(BigDecimal valor) {
        if (valor == null) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }
        return valor.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
