package com.consumoesperto.service;

import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.util.AppTimeZone;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransacaoRecorrenciaService {

    private final TransacaoRepository transacaoRepository;

    @Scheduled(cron = "0 0 2 * * ?", zone = "America/Sao_Paulo")
    public void processarTransacoesRecorrentes() {
        LocalDate hoje = AppTimeZone.hoje();
        List<Transacao> transacoesRecorrentes =
            transacaoRepository.findByRecorrenteTrueAndProximaExecucaoLessThanEqual(hoje);

        for (Transacao original : transacoesRecorrentes) {
            try {
                processarUma(original.getId(), hoje);
            } catch (Exception e) {
                log.warn("Recorrência falhou transacaoId={}: {}", original.getId(), e.getMessage());
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void processarUma(Long transacaoId, LocalDate hoje) {
        Transacao atual = transacaoRepository.findByIdForUpdate(transacaoId).orElse(null);
        if (atual == null || atual.isExcluido() || !Boolean.TRUE.equals(atual.isRecorrente())) {
            return;
        }
        if (atual.getFrequencia() == null || atual.getProximaExecucao() == null) {
            return;
        }
        if (atual.getProximaExecucao().isAfter(hoje)) {
            return;
        }
        if (atual.getUsuario() == null || atual.getCategoria() == null) {
            log.warn("Recorrência ignorada por dados incompletos. Transacao id={}", atual.getId());
            return;
        }

        LocalDateTime dataOcorrencia = atual.getProximaExecucao().atStartOfDay();
        boolean jaGerada = !transacaoRepository.findByUsuarioIdAndDescricaoAndDataTransacaoAndValor(
            atual.getUsuario().getId(),
            atual.getDescricao(),
            dataOcorrencia,
            atual.getValor()
        ).isEmpty();
        if (jaGerada) {
            log.info("Recorrência idempotente: ocorrência já existe idOrigem={} data={}", atual.getId(), dataOcorrencia);
            atual.setProximaExecucao(calcularProximaExecucao(atual.getProximaExecucao(), atual.getFrequencia()));
            transacaoRepository.save(atual);
            return;
        }

        Transacao novaTransacao = new Transacao();
        novaTransacao.setDescricao(atual.getDescricao());
        novaTransacao.setValor(atual.getValor());
        novaTransacao.setTipoTransacao(atual.getTipoTransacao());
        novaTransacao.setCategoria(atual.getCategoria());
        novaTransacao.setUsuario(atual.getUsuario());
        novaTransacao.setDataTransacao(atual.getProximaExecucao().atStartOfDay());
        novaTransacao.setRecorrente(false);
        novaTransacao.setExcluido(false);
        novaTransacao.setStatusConferencia(Transacao.StatusConferencia.CONFIRMADA);
        transacaoRepository.save(novaTransacao);

        atual.setProximaExecucao(calcularProximaExecucao(atual.getProximaExecucao(), atual.getFrequencia()));
        transacaoRepository.save(atual);
        log.info("Recorrência gerada idOrigem={} data={}", atual.getId(), novaTransacao.getDataTransacao());
    }

    private LocalDate calcularProximaExecucao(LocalDate base, Transacao.FrequenciaRecorrencia frequencia) {
        if (frequencia == Transacao.FrequenciaRecorrencia.SEMANAL) {
            return base.plusWeeks(1);
        }
        return base.plusMonths(1);
    }
}
