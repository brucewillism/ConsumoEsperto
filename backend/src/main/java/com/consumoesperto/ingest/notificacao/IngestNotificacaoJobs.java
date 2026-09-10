package com.consumoesperto.ingest.notificacao;

import com.consumoesperto.config.IngestNotificacaoProperties;
import com.consumoesperto.repository.NotificacaoBancariaRecebidaRepository;
import com.consumoesperto.util.AppTimeZone;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class IngestNotificacaoJobs {

    private final IngestNotificacaoProperties properties;
    private final NotificacaoBancariaRecebidaRepository recebidaRepository;
    private final IngestNotificacaoAvisoService avisoService;

    @Scheduled(cron = "0 * * * * *", zone = "America/Sao_Paulo")
    public void despejarAvisos() {
        if (!properties.isEnabled() || !properties.isJobs()) {
            return;
        }
        try {
            avisoService.despejarPendentes();
        } catch (Exception e) {
            log.debug("ingest_aviso_flush: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 20 4 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public void expurgar() {
        if (!properties.isEnabled() || !properties.isJobs()) {
            return;
        }
        int dias = Math.max(7, properties.getRetentionDays());
        int n = recebidaRepository.deleteByCriadoEmBefore(AppTimeZone.agora().minusDays(dias));
        if (n > 0) {
            log.info("ingest_notificacao_expurgo removidas={}", n);
        }
    }
}
