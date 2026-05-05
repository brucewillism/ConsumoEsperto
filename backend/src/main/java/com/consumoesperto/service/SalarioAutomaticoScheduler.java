package com.consumoesperto.service;

import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.model.RendaConfig;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.RendaConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Lança receita confirmada no dia de pagamento, quando o utilizador activou a opção no WhatsApp ou na API.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SalarioAutomaticoScheduler {

    private static final ZoneId ZONA_BR = ZoneId.of("America/Sao_Paulo");

    private final RendaConfigRepository rendaConfigRepository;
    private final TransacaoService transacaoService;

    @Scheduled(cron = "0 0 9 * * ?", zone = "America/Sao_Paulo")
    public void lancarReceitasSalariais() {
        LocalDate hoje = LocalDate.now(ZONA_BR);
        int ym = hoje.getYear() * 100 + hoje.getMonthValue();
        int diaMes = hoje.getDayOfMonth();
        List<RendaConfig> configs;
        try {
            configs = rendaConfigRepository.findByReceitaAutomaticaAtivaIsTrue();
        } catch (Exception e) {
            log.warn("Salário automático: falha ao listar configs: {}", e.getMessage());
            return;
        }
        for (RendaConfig cfg : configs) {
            try {
                if (cfg.getDiaPagamento() == null || cfg.getDiaPagamento() != diaMes) {
                    continue;
                }
                if (cfg.getSalarioLiquido() == null || cfg.getSalarioLiquido().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                Integer ultimo = cfg.getUltimoMesLancamentoAuto();
                if (ultimo != null && ultimo == ym) {
                    continue;
                }
                Usuario u = cfg.getUsuario();
                if (u == null) {
                    continue;
                }
                Long uid = u.getId();
                TransacaoDTO dto = new TransacaoDTO();
                dto.setDescricao("Salário líquido (automático)");
                dto.setValor(cfg.getSalarioLiquido());
                dto.setTipoTransacao(TransacaoDTO.TipoTransacao.RECEITA);
                dto.setDataTransacao(LocalDateTime.now(ZONA_BR));
                dto.setStatusConferencia(TransacaoDTO.StatusConferencia.CONFIRMADA);
                transacaoService.criarTransacao(dto, uid);
                cfg.setUltimoMesLancamentoAuto(ym);
                rendaConfigRepository.save(cfg);
                log.info("Salário automático lançado userId={} valor={} ym={}", uid, cfg.getSalarioLiquido(), ym);
            } catch (Exception e) {
                log.warn("Salário automático falhou configId={}: {}", cfg.getId(), e.getMessage());
            }
        }
    }
}
