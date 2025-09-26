package com.consumoesperto.controller;

import com.consumoesperto.model.AutorizacaoBancaria;
import com.consumoesperto.model.BankApiConfig;
import com.consumoesperto.repository.AutorizacaoBancariaRepository;
import com.consumoesperto.repository.BankApiConfigRepository;
import com.consumoesperto.service.AuthService;
import com.consumoesperto.service.MercadoPagoDataSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/secure/mercadopago")
@RequiredArgsConstructor
@Slf4j
public class SecureMercadoPagoController {

    private final BankApiConfigRepository bankApiConfigRepository;
    private final AutorizacaoBancariaRepository autorizacaoBancariaRepository;
    private final MercadoPagoDataSyncService dataSyncService;
    private final AuthService authService;
    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/dados")
    @CrossOrigin(origins = {"http://localhost:4200", "https://22e294954ab2.ngrok-free.app"})
    public Map<String, Object> obterDadosMercadoPago(HttpServletRequest request) {
        try {
            // Verificar autenticação
            Long usuarioId = authService.getCurrentUserId();
            log.info("🔐 Usuário autenticado: {}", usuarioId);

            // Buscar configuração do Mercado Pago
            Optional<BankApiConfig> configOpt = bankApiConfigRepository.findByAtivoTrue().stream()
                .filter(config -> "MERCADO_PAGO".equals(config.getTipoBanco()))
                .filter(config -> config.getUsuario().getId().equals(usuarioId))
                .findFirst();
            
            if (!configOpt.isPresent()) {
                return Map.of(
                    "status", "error",
                    "message", "Configuração do Mercado Pago não encontrada para este usuário"
                );
            }

            BankApiConfig config = configOpt.get();
            
            // Buscar autorização bancária
            Optional<AutorizacaoBancaria> authOpt = autorizacaoBancariaRepository
                .findByUsuarioIdAndTipoBanco(usuarioId, "MERCADO_PAGO");
            
            if (!authOpt.isPresent()) {
                return Map.of(
                    "status", "error",
                    "message", "Autorização do Mercado Pago não encontrada. Faça a autorização primeiro."
                );
            }

            AutorizacaoBancaria auth = authOpt.get();
            
            // Buscar dados do Mercado Pago
            Map<String, Object> resultado = buscarDadosMercadoPago(auth);
            
            // Sincronizar dados no banco
            sincronizarDadosNoBanco(resultado, usuarioId);
            
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
            
            if (pagamentosResponse.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> pagamentosData = pagamentosResponse.getBody();
                resultado = Map.of(
                    "pagamentos", pagamentosData.get("results"),
                    "total_pagamentos", pagamentosData.get("paging")
                );
            }
            
        } catch (Exception e) {
            log.error("Erro ao buscar dados do Mercado Pago: {}", e.getMessage());
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
            log.error("Erro na sincronização: {}", e.getMessage());
        }
    }
}
