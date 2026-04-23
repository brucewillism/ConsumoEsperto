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

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/public/data")
@RequiredArgsConstructor
@Slf4j
public class PublicDataController {

    private final BankApiConfigRepository bankApiConfigRepository;
    private final AutorizacaoBancariaRepository autorizacaoBancariaRepository;
    private final MercadoPagoDataSyncService dataSyncService;
    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/mercadopago/sync")
    @CrossOrigin(origins = {"http://localhost:4200", "https://0d723f1e294f.ngrok-free.app"})
    public Map<String, Object> sincronizarDadosMercadoPago() {
        try {
            log.info("🚀 INICIANDO SINCRONIZAÇÃO DE DADOS REAIS DO MERCADO PAGO");

            // Buscar configuração do Mercado Pago (usuário 1)
            Optional<BankApiConfig> configOpt = bankApiConfigRepository.findByAtivoTrue().stream()
                .filter(config -> "MERCADO_PAGO".equals(config.getTipoBanco()))
                .findFirst();
            
            if (!configOpt.isPresent()) {
                return Map.of(
                    "status", "error",
                    "message", "Configuração do Mercado Pago não encontrada"
                );
            }

            BankApiConfig config = configOpt.get();
            log.info("✅ Configuração encontrada: Usuário {}", config.getUsuario().getId());
            
            // Buscar autorização bancária
            Optional<AutorizacaoBancaria> authOpt = autorizacaoBancariaRepository
                .findByUsuarioIdAndTipoBanco(config.getUsuario().getId(), "MERCADO_PAGO");
            
            if (!authOpt.isPresent()) {
                return Map.of(
                    "status", "error",
                    "message", "Autorização do Mercado Pago não encontrada"
                );
            }

            AutorizacaoBancaria auth = authOpt.get();
            log.info("✅ Autorização encontrada: Token {}...", auth.getAccessToken().substring(0, 20));
            
            // Buscar dados do Mercado Pago
            Map<String, Object> resultado = buscarDadosMercadoPago(auth);
            
            // Sincronizar dados no banco
            sincronizarDadosNoBanco(resultado, config.getUsuario().getId());
            
            return Map.of(
                "status", "success",
                "message", "Dados obtidos e sincronizados com sucesso",
                "dados", resultado
            );
            
        } catch (Exception e) {
            log.error("❌ Erro ao obter dados do Mercado Pago: {}", e.getMessage(), e);
            return Map.of(
                "status", "error",
                "message", e.getMessage()
            );
        }
    }

    private Map<String, Object> buscarDadosMercadoPago(AutorizacaoBancaria auth) {
        Map<String, Object> resultado = Map.of();
        
        try {
            // Buscar pagamentos
            String pagamentosUrl = "https://api.mercadopago.com/v1/payments/search?limit=10";
            HttpHeaders pagamentosHeaders = new HttpHeaders();
            pagamentosHeaders.set("Authorization", "Bearer " + auth.getAccessToken());
            pagamentosHeaders.set("Content-Type", "application/json");
            
            HttpEntity<String> pagamentosRequest = new HttpEntity<>(pagamentosHeaders);
            ResponseEntity<Map> pagamentosResponse = restTemplate.exchange(
                pagamentosUrl, HttpMethod.GET, pagamentosRequest, Map.class);
            
            log.info("📡 Resposta da API: Status {}", pagamentosResponse.getStatusCode());
            
            if (pagamentosResponse.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> pagamentosData = pagamentosResponse.getBody();
                List<Map<String, Object>> pagamentos = (List<Map<String, Object>>) pagamentosData.get("results");
                
                log.info("✅ Encontrados {} pagamentos reais", pagamentos.size());
                
                resultado = Map.of(
                    "pagamentos", pagamentos,
                    "total_pagamentos", pagamentosData.get("paging"),
                    "count", pagamentos.size()
                );
            } else {
                log.error("❌ Erro na API: {}", pagamentosResponse.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("Erro ao buscar dados do Mercado Pago: {}", e.getMessage(), e);
        }
        
        return resultado;
    }

    private void sincronizarDadosNoBanco(Map<String, Object> dados, Long usuarioId) {
        try {
            if (dados.containsKey("pagamentos")) {
                List<Map<String, Object>> pagamentos = (List<Map<String, Object>>) dados.get("pagamentos");
                dataSyncService.sincronizarPagamentos(usuarioId, pagamentos);
                log.info("✅ {} pagamentos sincronizados para usuário {}", pagamentos.size(), usuarioId);
            }
        } catch (Exception e) {
            log.error("Erro na sincronização: {}", e.getMessage(), e);
        }
    }
}
