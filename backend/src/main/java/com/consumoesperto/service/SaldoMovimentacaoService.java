package com.consumoesperto.service;

import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.ContaBancariaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Atualiza {@link ContaBancaria#getSaldoAtual()} conforme o ciclo de vida das transações confirmadas.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SaldoMovimentacaoService {

    private static final int SCALE = 2;

    private final ContaBancariaRepository contaBancariaRepository;

    /** Snapshot imutável para estorno/recálculo em edições. */
    public record MovimentacaoSnapshot(
        Long contaBancariaId,
        BigDecimal valor,
        Transacao.TipoTransacao tipoTransacao,
        Transacao.StatusConferencia statusConferencia,
        Long faturaId
    ) {
        static MovimentacaoSnapshot from(Transacao t) {
            if (t == null) {
                return null;
            }
            return new MovimentacaoSnapshot(
                t.getContaBancaria() != null ? t.getContaBancaria().getId() : null,
                t.getValor(),
                t.getTipoTransacao(),
                t.getStatusConferencia(),
                t.getFatura() != null ? t.getFatura().getId() : null
            );
        }
    }

    public MovimentacaoSnapshot capturarSnapshot(Transacao transacao) {
        return MovimentacaoSnapshot.from(transacao);
    }

    @Transactional
    public void aplicarCriacao(Transacao transacao) {
        BigDecimal delta = impactoConfirmado(transacao);
        if (delta.compareTo(BigDecimal.ZERO) == 0 || transacao.getContaBancaria() == null) {
            return;
        }
        aplicarDelta(transacao.getContaBancaria().getId(), delta);
    }

    @Transactional
    public void sincronizarMovimentacao(MovimentacaoSnapshot antes, Transacao depois) {
        if (antes != null) {
            BigDecimal impactoAnterior = impactoConfirmado(antes);
            if (impactoAnterior.compareTo(BigDecimal.ZERO) != 0 && antes.contaBancariaId() != null) {
                aplicarDelta(antes.contaBancariaId(), impactoAnterior.negate());
            }
        }
        aplicarCriacao(depois);
    }

    @Transactional
    public void aplicarExclusao(Transacao transacao) {
        BigDecimal impacto = impactoConfirmado(transacao);
        if (impacto.compareTo(BigDecimal.ZERO) == 0 || transacao.getContaBancaria() == null) {
            return;
        }
        aplicarDelta(transacao.getContaBancaria().getId(), impacto.negate());
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
        aplicarDelta(contaOrigemId, v.negate());
        aplicarDelta(contaDestinoId, v);
        log.info("[MULTICARTEIRA] Transferência {} → {} valor {}", contaOrigemId, contaDestinoId, v);
    }

    private void aplicarDelta(Long contaId, BigDecimal delta) {
        ContaBancaria conta = contaBancariaRepository.findById(contaId)
            .orElseThrow(() -> new RuntimeException("Conta bancária não encontrada: " + contaId));
        BigDecimal saldo = scale(conta.getSaldoAtual()).add(scale(delta));
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
        log.debug("[MULTICARTEIRA] Conta {} saldo → {} (delta {})", contaId, saldo, delta);
    }

    /** Só movimenta conta quando confirmada, com carteira; despesas de cartão/fatura não movimentam, exceto PAGAMENTO_FATURA. */
    BigDecimal impactoConfirmado(Transacao transacao) {
        if (transacao == null) {
            return BigDecimal.ZERO;
        }
        return impactoConfirmado(MovimentacaoSnapshot.from(transacao));
    }

    private BigDecimal impactoConfirmado(MovimentacaoSnapshot snap) {
        if (snap == null || snap.contaBancariaId() == null) {
            return BigDecimal.ZERO;
        }
        if (snap.statusConferencia() != Transacao.StatusConferencia.CONFIRMADA) {
            return BigDecimal.ZERO;
        }
        if (snap.faturaId() != null
            && snap.tipoTransacao() != Transacao.TipoTransacao.PAGAMENTO_FATURA) {
            return BigDecimal.ZERO;
        }
        if (snap.tipoTransacao() == null || snap.valor() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal valor = scale(snap.valor());
        return switch (snap.tipoTransacao()) {
            case RECEITA -> valor;
            case DESPESA, INVESTIMENTO, PAGAMENTO_FATURA -> valor.negate();
        };
    }

    private static BigDecimal scale(BigDecimal valor) {
        if (valor == null) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }
        return valor.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
