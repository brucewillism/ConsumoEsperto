package com.consumoesperto.service;

import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransacaoRecorrenciaService {

    private final TransacaoRepository transacaoRepository;

    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void processarTransacoesRecorrentes() {
        LocalDate hoje = LocalDate.now();
        List<Transacao> transacoesRecorrentes =
            transacaoRepository.findByRecorrenteTrueAndProximaExecucaoLessThanEqual(hoje);

        for (Transacao original : transacoesRecorrentes) {
            Transacao atual = transacaoRepository.findById(original.getId()).orElse(null);
            if (atual == null || atual.isExcluido()) {
                continue;
            }
            if (original.getFrequencia() == null || original.getProximaExecucao() == null) {
                continue;
            }
            if (atual.getUsuario() == null || atual.getCategoria() == null) {
                log.warn("Recorrência ignorada por dados incompletos. Transacao id={}", atual.getId());
                continue;
            }

            Transacao novaTransacao = new Transacao();
            novaTransacao.setId(null);
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
        }

        if (!transacoesRecorrentes.isEmpty()) {
            log.info("Recorrência processada: {} transações geradas em {}", transacoesRecorrentes.size(), LocalDateTime.now());
        }
    }

    private LocalDate calcularProximaExecucao(LocalDate base, Transacao.FrequenciaRecorrencia frequencia) {
        if (frequencia == Transacao.FrequenciaRecorrencia.SEMANAL) {
            return base.plusWeeks(1);
        }
        return base.plusMonths(1);
    }
}
