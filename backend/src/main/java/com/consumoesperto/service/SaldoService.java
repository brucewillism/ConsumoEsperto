package com.consumoesperto.service;

import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Saldo exibido no dashboard: receitas confirmadas − despesas confirmadas (inclui cartão na fatura).
 * O limite de crédito nunca é somado ao saldo.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SaldoService {

    private final TransacaoRepository transacaoRepository;

    /**
     * Saldo = soma RECEITA confirmada − soma DESPESA confirmada.
     */
    public BigDecimal saldoContaCorrente(Long usuarioId) {
        return saldoConfirmado(usuarioId);
    }

    /**
     * Alias explícito (receitas − despesas confirmadas).
     */
    public BigDecimal saldoConfirmado(Long usuarioId) {
        BigDecimal r = transacaoRepository.sumValorConfirmadaByUsuarioIdAndTipoTransacao(
            usuarioId, Transacao.TipoTransacao.RECEITA);
        BigDecimal d = transacaoRepository.sumValorConfirmadaByUsuarioIdAndTipoTransacao(
            usuarioId, Transacao.TipoTransacao.DESPESA);
        r = r != null ? r : BigDecimal.ZERO;
        d = d != null ? d : BigDecimal.ZERO;
        return r.subtract(d);
    }

    public void notificarAlteracaoSaldo(Long usuarioId) {
        if (usuarioId == null) {
            return;
        }
        BigDecimal s = saldoContaCorrente(usuarioId);
        log.debug("[SALDO] Utilizador {} — saldo (receitas − despesas confirmadas): {}", usuarioId, s);
    }
}
