package com.consumoesperto.ingest.notificacao;

import com.consumoesperto.mobilecapture.service.MerchantCategoryRuleService;
import com.consumoesperto.service.jarvis.CategoriaCorrecaoMemoriaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngestNotificacaoCategorizador {

    private final CategoriaCorrecaoMemoriaService correcaoMemoriaService;
    private final MerchantCategoryRuleService merchantCategoryRuleService;

    public Optional<Long> sugerir(Long usuarioId, String estabelecimento) {
        try {
            Optional<Long> correcao = correcaoMemoriaService.sugerirCategoriaPorCorrecao(usuarioId, estabelecimento);
            if (correcao.isPresent()) {
                return correcao;
            }
        } catch (Exception e) {
            log.debug("CORRECAO indisponível na ingestão: {}", e.getMessage());
        }
        try {
            return merchantCategoryRuleService.match(usuarioId, estabelecimento)
                .map(MerchantCategoryRuleService.CategoryMatch::categoriaId);
        } catch (Exception e) {
            log.debug("Regra de merchant indisponível na ingestão: {}", e.getMessage());
            return Optional.empty();
        }
    }
}

