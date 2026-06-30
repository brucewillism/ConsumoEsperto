package com.consumoesperto.service;

import com.consumoesperto.model.RendaConfig;
import com.consumoesperto.repository.RendaConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Verifica no dia de pagamento (00h01, 9h, 12h e 18h BRT) se há salários automáticos pendentes.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SalarioAutomaticoScheduler {

    private final RendaConfigRepository rendaConfigRepository;
    private final SalarioAutomaticoService salarioAutomaticoService;

    /** 00h01 — credita salário no dia de pagamento assim que vira o dia civil (ex.: 30 → 00h01). */
    @Scheduled(cron = "0 1 0 * * ?", zone = "America/Sao_Paulo")
    public void lancarReceitasSalariaisMeiaNoite() {
        lancarReceitasSalariais();
    }

    @Scheduled(cron = "0 0 9,12,18 * * ?", zone = "America/Sao_Paulo")
    public void lancarReceitasSalariais() {
        List<RendaConfig> configs;
        try {
            configs = rendaConfigRepository.findByReceitaAutomaticaAtivaIsTrue();
        } catch (Exception e) {
            log.warn("Salário automático: falha ao listar configs: {}", e.getMessage());
            return;
        }
        for (RendaConfig cfg : configs) {
            try {
                salarioAutomaticoService.tentarLancarSalarioMesAtual(cfg);
            } catch (Exception e) {
                log.warn("Salário automático falhou configId={}: {}", cfg.getId(), e.getMessage());
            }
        }
    }
}
