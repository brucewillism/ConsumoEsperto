package com.consumoesperto.service;

import com.consumoesperto.model.AgendamentoExecucao;
import com.consumoesperto.repository.AgendamentoExecucaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Semáforo de idempotência dos agendamentos, apoiado no índice único
 * {@code ux_agendamento_execucoes_competencia} do PostgreSQL.
 *
 * <p>O registro participa da MESMA transação do processamento
 * ({@code Propagation.MANDATORY}): não pode usar {@code REQUIRES_NEW} porque o
 * INSERT precisa de {@code FOR KEY SHARE} na linha do agendamento (FK), que
 * conflita com o {@code FOR UPDATE} já detido pela transação de negócio —
 * transação separada ficaria bloqueada esperando a externa (deadlock).
 * Na mesma transação: conflito de unicidade propaga como
 * {@link org.springframework.dao.DataIntegrityViolationException} (tratado
 * pelo chamador como "já processado") e falha no débito desfaz o registro
 * automaticamente via rollback.</p>
 */
@Service
@RequiredArgsConstructor
public class AgendamentoExecucaoRegistroService {

    private final AgendamentoExecucaoRepository repository;

    /**
     * Registra a execução da competência dentro da transação de processamento.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException quando a
     *         competência já foi registrada por outro nó/thread (chave única)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(Long agendamentoId, LocalDate dataExecucao, AgendamentoExecucao.TipoExecucao tipo) {
        repository.saveAndFlush(new AgendamentoExecucao(agendamentoId, dataExecucao, tipo));
    }
}
