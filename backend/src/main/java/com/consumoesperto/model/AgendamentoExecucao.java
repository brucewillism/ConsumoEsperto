package com.consumoesperto.model;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Registro de execução de um agendamento de pagamento em uma competência.
 *
 * <p>Atua como semáforo de idempotência no banco: o índice único
 * {@code (agendamento_id, data_execucao)} garante exatamente um débito por
 * agendamento + competência, mesmo com dois nós, retry, restart ou execução
 * manual simultânea ao scheduler.</p>
 */
@Entity
@Table(
    name = "agendamento_execucoes",
    uniqueConstraints = @UniqueConstraint(
        name = "ux_agendamento_execucoes_competencia",
        columnNames = {"agendamento_id", "data_execucao"}
    )
)
public class AgendamentoExecucao {

    public enum TipoExecucao {
        AUTOMATICA,
        MANUAL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agendamento_id", nullable = false)
    private Long agendamentoId;

    /** Competência processada — a data de vencimento debitada. */
    @Column(name = "data_execucao", nullable = false)
    private LocalDate dataExecucao;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_execucao", nullable = false, length = 16)
    private TipoExecucao tipoExecucao = TipoExecucao.AUTOMATICA;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    protected AgendamentoExecucao() {
    }

    public AgendamentoExecucao(Long agendamentoId, LocalDate dataExecucao, TipoExecucao tipoExecucao) {
        this.agendamentoId = agendamentoId;
        this.dataExecucao = dataExecucao;
        this.tipoExecucao = tipoExecucao != null ? tipoExecucao : TipoExecucao.AUTOMATICA;
    }

    public Long getId() {
        return id;
    }

    public Long getAgendamentoId() {
        return agendamentoId;
    }

    public LocalDate getDataExecucao() {
        return dataExecucao;
    }

    public TipoExecucao getTipoExecucao() {
        return tipoExecucao;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }
}
