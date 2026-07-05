package com.consumoesperto.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Trilha de auditoria append-only de toda mutação de saldo de conta.
 * Linhas nunca são atualizadas nem removidas (exceto expurgo por retenção).
 */
@Entity
@Table(name = "movimentacao_saldo_log")
@Getter
@Setter
public class MovimentacaoSaldoLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conta_id", nullable = false)
    private Long contaId;

    @Column(name = "transacao_id")
    private Long transacaoId;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "delta", nullable = false, precision = 19, scale = 2)
    private BigDecimal delta;

    @Column(name = "saldo_antes", nullable = false, precision = 19, scale = 2)
    private BigDecimal saldoAntes;

    @Column(name = "saldo_depois", nullable = false, precision = 19, scale = 2)
    private BigDecimal saldoDepois;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", nullable = false, length = 20)
    private OrigemMovimentacaoSaldo origem;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_operacao", nullable = false, length = 32)
    private TipoOperacaoSaldo tipoOperacao;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    public enum OrigemMovimentacaoSaldo {
        APP,
        WHATSAPP,
        JOB,
        REPARO,
        IMPORTACAO,
        SISTEMA
    }

    public enum TipoOperacaoSaldo {
        CRIACAO,
        EDICAO,
        EXCLUSAO,
        CREDITO_DIRETO,
        TRANSFERENCIA_SAIDA,
        TRANSFERENCIA_ENTRADA,
        RECONCILIACAO
    }
}
