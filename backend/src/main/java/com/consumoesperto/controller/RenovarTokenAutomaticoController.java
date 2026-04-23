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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Slf4j
public class RenovarTokenAutomaticoController {

    private final BankApiConfigRepository bankApiConfigRepository;
    private final AutorizacaoBancariaRepository autorizacaoBancariaRepository;
    private final MercadoPagoDataSyncService dataSyncService;
    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/renovar-token-mercadopago")
    @CrossOrigin(origins = {"http://localhost:4200", "https://0d723f1e294f.ngrok-free.app"})
    public Map<String, Object> renovarTokenMercadoPago() {
        try {
            log.info("🔄 INICIANDO RENOVAÇÃO AUTOMÁTICA DO TOKEN DO MERCADO PAGO");
            log.info("=" .repeat(80));
            
            // 1. Buscar configuração do Mercado Pago
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
            
            // 2. Buscar autorização bancária atual
            Optional<AutorizacaoBancaria> authOpt = autorizacaoBancariaRepository
                .findByUsuarioIdAndTipoBanco(config.getUsuario().getId(), "MERCADO_PAGO");
            
            if (!authOpt.isPresent()) {
                return Map.of(
                    "status", "error",
                    "message", "Autorização do Mercado Pago não encontrada"
                );
            }
            
            AutorizacaoBancaria auth = authOpt.get();
            log.info("📋 Token atual: {}...", auth.getAccessToken().substring(0, 20));
            log.info("📅 Data expiração atual: {}", auth.getDataExpiracao());
            
            // 3. Gerar novo token usando OAuth2
            log.info("\n🚀 GERANDO NOVO TOKEN DE PRODUÇÃO");
            log.info("-" .repeat(50));
            
            String tokenUrl = "https://api.mercadopago.com/oauth/token";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/x-www-form-urlencoded");
            
            String body = String.format(
                "grant_type=client_credentials&client_id=%s&client_secret=%s",
                config.getClientId(),
                config.getClientSecret()
            );
            
            HttpEntity<String> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                tokenUrl, HttpMethod.POST, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> tokenData = response.getBody();
                String newAccessToken = (String) tokenData.get("access_token");
                String tokenType = (String) tokenData.get("token_type");
                Integer expiresIn = (Integer) tokenData.get("expires_in");
                
                log.info("✅ NOVO TOKEN GERADO COM SUCESSO!");
                log.info("   - Access Token: {}...", newAccessToken.substring(0, 20));
                log.info("   - Token Type: {}", tokenType);
                log.info("   - Expires In: {} segundos", expiresIn);
                
                // 4. Atualizar no banco de dados
                auth.setAccessToken(newAccessToken);
                auth.setTokenType(tokenType);
                auth.setDataExpiracao(LocalDateTime.now().plusSeconds(expiresIn));
                auth.setDataAtualizacao(LocalDateTime.now());
                autorizacaoBancariaRepository.save(auth);
                
                log.info("💾 Token atualizado no banco de dados");
                
                // 5. Testar o novo token buscando dados reais
                log.info("\n🧪 TESTANDO NOVO TOKEN COM DADOS REAIS");
                log.info("-" .repeat(50));
                
                Map<String, Object> dadosReais = testarTokenComDadosReais(newAccessToken);
                
                // 6. Sincronizar dados no banco
                if (dadosReais.containsKey("pagamentos")) {
                    List<Map<String, Object>> pagamentos = (List<Map<String, Object>>) dadosReais.get("pagamentos");
                    dataSyncService.sincronizarPagamentos(config.getUsuario().getId(), pagamentos);
                    log.info("✅ {} pagamentos sincronizados no banco!", pagamentos.size());
                }
                
                log.info("\n🎉 RENOVAÇÃO AUTOMÁTICA CONCLUÍDA COM SUCESSO!");
                log.info("=" .repeat(80));
                
                return Map.of(
                    "status", "success",
                    "message", "Token renovado e dados sincronizados com sucesso",
                    "novo_token", newAccessToken.substring(0, 20) + "...",
                    "nova_expiracao", auth.getDataExpiracao(),
                    "dados_sincronizados", dadosReais
                );
                
            } else {
                log.error("❌ Erro ao gerar novo token: {}", response.getStatusCode());
                return Map.of(
                    "status", "error",
                    "message", "Erro ao gerar novo token: " + response.getStatusCode()
                );
            }
            
        } catch (Exception e) {
            log.error("❌ Erro durante renovação automática: {}", e.getMessage(), e);
            return Map.of(
                "status", "error",
                "message", e.getMessage(),
                "tipo_erro", e.getClass().getSimpleName()
            );
        }
    }
    
    private Map<String, Object> testarTokenComDadosReais(String accessToken) {
        try {
            String pagamentosUrl = "https://api.mercadopago.com/v1/payments/search?limit=10";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            headers.set("Content-Type", "application/json");
            
            HttpEntity<String> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                pagamentosUrl, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> pagamentosData = response.getBody();
                List<Map<String, Object>> pagamentos = (List<Map<String, Object>>) pagamentosData.get("results");
                
                log.info("✅ {} pagamentos reais encontrados!", pagamentos.size());
                
                return Map.of(
                    "pagamentos", pagamentos,
                    "total_pagamentos", pagamentosData.get("paging"),
                    "count", pagamentos.size()
                );
            } else {
                log.error("❌ Erro ao testar token: {}", response.getStatusCode());
                return Map.of("error", "Erro ao testar token: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("Erro ao testar token: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }
}
