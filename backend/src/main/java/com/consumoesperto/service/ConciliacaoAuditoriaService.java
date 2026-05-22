package com.consumoesperto.service;

import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Auditoria de provisões fiscais/despesas estimadas vencidas que distorcem projeções.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConciliacaoAuditoriaService {

    public static final int DIAS_EXPIRACAO_PROVISAO_FANTASMA = 5;

    private final TransacaoRepository transacaoRepository;
    private final NotificacaoPushService notificacaoPushService;

    public LocalDateTime limiteProvisaoFantasma() {
        return LocalDate.now().minusDays(DIAS_EXPIRACAO_PROVISAO_FANTASMA).atStartOfDay();
    }

    @Transactional(readOnly = true)
    public List<Transacao> listarProvisoesFantasmas(Long usuarioId) {
        return transacaoRepository.findProvisoesFantasmas(usuarioId, limiteProvisaoFantasma());
    }

    @Transactional(readOnly = true)
    public Set<Long> idsExcluidosDaProjecao(Long usuarioId) {
        return listarProvisoesFantasmas(usuarioId).stream()
            .map(Transacao::getId)
            .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * Subtrai provisões fantasmas do valor bruto de receitas fiscais previstas no mês.
     */
    @Transactional(readOnly = true)
    public BigDecimal receitasFiscaisLiquidasNoMes(Long usuarioId, YearMonth ym, BigDecimal receitasFiscaisBrutas) {
        LocalDateTime inicio = ym.atDay(1).atStartOfDay();
        LocalDateTime fim = ym.atEndOfMonth().atTime(23, 59, 59);
        BigDecimal fantasmas = transacaoRepository.sumProvisoesFantasmasPeriodo(
            usuarioId, inicio, fim, limiteProvisaoFantasma());
        if (fantasmas == null) {
            fantasmas = BigDecimal.ZERO;
        }
        return receitasFiscaisBrutas.subtract(fantasmas).max(BigDecimal.ZERO)
            .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Job diário: marca provisões fantasmas como AUDITORIA e notifica o usuário (in-app).
     */
    @Transactional
    public int auditarProvisoesFantasmas(Long usuarioId) {
        List<Transacao> fantasmas = listarProvisoesFantasmas(usuarioId);
        int count = 0;
        for (Transacao t : fantasmas) {
            if (t.getStatusConferencia() != Transacao.StatusConferencia.PREVISTO) {
                continue;
            }
            t.setStatusConferencia(Transacao.StatusConferencia.AUDITORIA);
            transacaoRepository.save(t);
            notificacaoPushService.registrarProvisaoFantasma(
                usuarioId,
                t.getDescricao(),
                t.getValor(),
                t.getOrigemFiscal() != null ? "fiscal" : "despesa estimada"
            );
            count++;
            log.info("[AUDITORIA] Provisão fantasma userId={} transacaoId={} valor={}",
                usuarioId, t.getId(), t.getValor());
        }
        return count;
    }
}
