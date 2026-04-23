package com.consumoesperto.controller;

import com.consumoesperto.model.AutorizacaoBancaria;
import com.consumoesperto.model.BankApiConfig;
import com.consumoesperto.repository.AutorizacaoBancariaRepository;
import com.consumoesperto.repository.BankApiConfigRepository;
import com.consumoesperto.service.MercadoPagoDataSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/sincronizacao-completa")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:4200", "https://0d723f1e294f.ngrok-free.app"})
public class SincronizacaoCompletaController {

    private final BankApiConfigRepository bankApiConfigRepository;
    private final AutorizacaoBancariaRepository autorizacaoBancariaRepository;
    private final MercadoPagoDataSyncService dataSyncService;
    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/mercadopago")
    public Map<String, Object> sincronizarTodosPagamentos() {
        try {
            log.info("🚀 INICIANDO SINCRONIZAÇÃO COMPLETA DE TODOS OS PAGAMENTOS");
            log.info("=" .repeat(80));
            
            // 1. Buscar configuração do Mercado Pago
            Optional<BankApiConfig> configOpt = bankApiConfigRepository.findByAtivoTrue().stream()
                .filter(config -> "MERCADO_PAGO".equals(config.getTipoBanco()))
                .findFirst();
            
            if (!configOpt.isPresent()) {
                log.error("❌ NENHUMA CONFIGURAÇÃO DO MERCADO PAGO ENCONTRADA!");
                return Map.of(
                    "status", "error",
                    "message", "Nenhuma configuração do Mercado Pago encontrada"
                );
            }
            
            BankApiConfig config = configOpt.get();
            log.info("✅ CONFIGURAÇÃO ENCONTRADA para usuário: {}", config.getUsuario().getId());
            
            // 2. Buscar autorização bancária
            Optional<AutorizacaoBancaria> authOpt = autorizacaoBancariaRepository
                .findByUsuarioIdAndTipoBanco(config.getUsuario().getId(), "MERCADO_PAGO");
            
            if (!authOpt.isPresent()) {
                log.error("❌ NENHUMA AUTORIZAÇÃO BANCÁRIA ENCONTRADA!");
                return Map.of(
                    "status", "error",
                    "message", "Nenhuma autorização bancária encontrada"
                );
            }
            
            AutorizacaoBancaria auth = authOpt.get();
            log.info("✅ AUTORIZAÇÃO ENCONTRADA - Token válido até: {}", auth.getDataExpiracao());
            
            // 3. Buscar TODOS os pagamentos usando paginação
            List<Map<String, Object>> todosPagamentos = new ArrayList<>();
            int offset = 0;
            int limit = 50; // Máximo por página da API
            boolean temMaisPagamentos = true;
            
            while (temMaisPagamentos) {
                String pagamentosUrl = String.format(
                    "https://api.mercadopago.com/v1/payments/search?limit=%d&offset=%d", 
                    limit, offset
                );
                
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + auth.getAccessToken());
                headers.set("Content-Type", "application/json");
                
                HttpEntity<String> request = new HttpEntity<>(headers);
                ResponseEntity<Map> response = restTemplate.exchange(
                    pagamentosUrl, HttpMethod.GET, request, Map.class);
                
                if (response.getStatusCode().is2xxSuccessful()) {
                    Map<String, Object> data = response.getBody();
                    List<Map<String, Object>> pagamentos = (List<Map<String, Object>>) data.get("results");
                    
                    if (pagamentos != null && !pagamentos.isEmpty()) {
                        todosPagamentos.addAll(pagamentos);
                        log.info("📄 Página {}: {} pagamentos encontrados (Total: {})", 
                            (offset/limit) + 1, pagamentos.size(), todosPagamentos.size());
                        
                        // Verificar se há mais páginas
                        Map<String, Object> paging = (Map<String, Object>) data.get("paging");
                        if (paging != null) {
                            Integer total = (Integer) paging.get("total");
                            Integer offsetAtual = (Integer) paging.get("offset");
                            Integer limitAtual = (Integer) paging.get("limit");
                            
                            if (total != null && offsetAtual != null && limitAtual != null) {
                                if (offsetAtual + limitAtual >= total) {
                                    temMaisPagamentos = false;
                                } else {
                                    offset += limit;
                                }
                            } else {
                                temMaisPagamentos = false;
                            }
                        } else {
                            temMaisPagamentos = false;
                        }
                    } else {
                        temMaisPagamentos = false;
                    }
                } else {
                    log.error("❌ Erro na API: {}", response.getStatusCode());
                    break;
                }
            }
            
            log.info("🎉 TOTAL DE PAGAMENTOS ENCONTRADOS: {}", todosPagamentos.size());
            
            // 4. Sincronizar TODOS os pagamentos no banco
            try {
                dataSyncService.sincronizarPagamentos(config.getUsuario().getId(), todosPagamentos);
                log.info("✅ {} pagamentos sincronizados no banco!", todosPagamentos.size());
            } catch (Exception e) {
                log.error("❌ Erro na sincronização: {}", e.getMessage());
                return Map.of(
                    "status", "error",
                    "message", "Erro na sincronização: " + e.getMessage(),
                    "total_encontrados", todosPagamentos.size()
                );
            }
            
            log.info("🎉 SINCRONIZAÇÃO COMPLETA FINALIZADA!");
            log.info("=" .repeat(80));
            
            return Map.of(
                "status", "success",
                "message", "Sincronização completa realizada com sucesso",
                "total_pagamentos", todosPagamentos.size(),
                "sincronizados", true
            );
            
        } catch (Exception e) {
            log.error("❌ ERRO DURANTE A SINCRONIZAÇÃO COMPLETA: {}", e.getMessage(), e);
            return Map.of(
                "status", "error",
                "message", e.getMessage(),
                "tipo_erro", e.getClass().getSimpleName()
            );
        }
    }
}
