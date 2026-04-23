package com.consumoesperto.service;

import com.consumoesperto.model.AutorizacaoBancaria;
import com.consumoesperto.repository.AutorizacaoBancariaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.Arrays;

/**
 * Serviço para buscar saldo bancário do Mercado Pago
 * 
 * Este serviço busca informações de saldo e conta do usuário
 * através da API do Mercado Pago.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoBalanceService {

    private final RestTemplate restTemplate;
    private final AutorizacaoBancariaRepository autorizacaoBancariaRepository;

    /**
     * Busca saldo bancário do usuário
     */
    public Map<String, Object> buscarSaldoBancario(Long usuarioId) {
        try {
            log.info("💰 Buscando saldo bancário para usuário: {}", usuarioId);
            
            // Buscar autorização bancária
            Optional<AutorizacaoBancaria> authOpt = autorizacaoBancariaRepository
                .findByUsuarioIdAndTipoBanco(usuarioId, "MERCADO_PAGO");
            
            if (!authOpt.isPresent()) {
                Map<String, Object> response = new HashMap<>();
                response.put("erro", "Usuário não possui autorização bancária do Mercado Pago");
                response.put("solucao", "Fazer OAuth2 primeiro");
                return response;
            }
            
            AutorizacaoBancaria auth = authOpt.get();
            String accessToken = auth.getAccessToken();
            
            // Verificar se é token temporário
            if (isTemporaryToken(accessToken)) {
                Map<String, Object> response = new HashMap<>();
                response.put("erro", "Token temporário detectado");
                response.put("token", accessToken);
                response.put("solucao", "Fazer OAuth2 para obter token real");
                return response;
            }
            
            // Buscar saldo real da API
            Map<String, Object> saldo = buscarSaldoReal(accessToken);
            
            if (saldo != null && !saldo.isEmpty()) {
                log.info("✅ Saldo encontrado para usuário: {}", usuarioId);
                return saldo;
            } else {
                log.warn("⚠️ Nenhum saldo encontrado para usuário: {}", usuarioId);
                
                // Retornar saldo simulado para demonstração
                return new HashMap<>();
            }
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar saldo bancário: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("erro", "Erro interno: " + e.getMessage());
            response.put("saldo_real", new HashMap<>());
            return response;
        }
    }
    
    /**
     * Busca saldo real da API do Mercado Pago
     */
    private Map<String, Object> buscarSaldoReal(String accessToken) {
        try {
            log.info("🔍 Buscando saldo real da API do Mercado Pago...");
            
            // Tentar diferentes endpoints para saldo
            List<String> endpoints = Arrays.asList(
                "https://api.mercadopago.com/v1/account/balance",
                "https://api.mercadopago.com/v1/users/me",
                "https://api.mercadopago.com/v1/account/settings",
                "https://api.mercadopago.com/v1/account/movements"
            );
            
            for (String endpoint : endpoints) {
                try {
                    HttpHeaders headers = new HttpHeaders();
                    headers.set("Authorization", "Bearer " + accessToken);
                    headers.set("Content-Type", "application/json");
                    
                    HttpEntity<String> request = new HttpEntity<>(headers);
                    ResponseEntity<Map> response = restTemplate.exchange(
                            endpoint, HttpMethod.GET, request, Map.class);
                    
                    if (response.getStatusCode().is2xxSuccessful()) {
                        log.info("✅ Endpoint funcionou: {}", endpoint);
                        
                        Map<String, Object> responseBody = response.getBody();
                        if (responseBody != null) {
                            return processarSaldoResponse(responseBody, endpoint);
                        }
                    } else {
                        log.warn("⚠️ Endpoint falhou: {} - Status: {}", endpoint, response.getStatusCode());
                    }
                    
                } catch (Exception e) {
                    log.warn("⚠️ Erro no endpoint {}: {}", endpoint, e.getMessage());
                }
            }
            
            log.warn("⚠️ Todos os endpoints de saldo falharam");
            return null;
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar saldo real: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Processa a resposta da API para extrair saldo
     */
    private Map<String, Object> processarSaldoResponse(Map<String, Object> responseBody, String endpoint) {
        Map<String, Object> saldo = new HashMap<>();
        
        try {
            if (endpoint.contains("balance")) {
                // Endpoint de saldo
                if (responseBody.containsKey("available_balance")) {
                    Object balance = responseBody.get("available_balance");
                    if (balance instanceof Map) {
                        Map<String, Object> balanceData = (Map<String, Object>) balance;
                        saldo.put("saldo_disponivel", balanceData.get("amount"));
                        saldo.put("moeda", balanceData.get("currency"));
                    }
                }
                
                if (responseBody.containsKey("pending_balance")) {
                    Object pending = responseBody.get("pending_balance");
                    if (pending instanceof Map) {
                        Map<String, Object> pendingData = (Map<String, Object>) pending;
                        saldo.put("saldo_pendente", pendingData.get("amount"));
                    }
                }
                
            } else if (endpoint.contains("users/me")) {
                // Endpoint de informações do usuário
                saldo.put("usuario_id", responseBody.get("id"));
                saldo.put("email", responseBody.get("email"));
                saldo.put("nome", responseBody.get("first_name") + " " + responseBody.get("last_name"));
                
            } else if (endpoint.contains("movements")) {
                // Endpoint de movimentações
                if (responseBody.containsKey("results") && responseBody.get("results") instanceof List) {
                    List<Map<String, Object>> movements = (List<Map<String, Object>>) responseBody.get("results");
                    saldo.put("movimentacoes", movements.size());
                    
                    // Calcular saldo baseado nas movimentações
                    BigDecimal saldoCalculado = BigDecimal.ZERO;
                    for (Map<String, Object> movement : movements) {
                        if (movement.containsKey("amount")) {
                            Object amount = movement.get("amount");
                            if (amount instanceof Number) {
                                saldoCalculado = saldoCalculado.add(new BigDecimal(amount.toString()));
                            }
                        }
                    }
                    saldo.put("saldo_calculado", saldoCalculado);
                }
            }
            
            saldo.put("fonte", endpoint);
            saldo.put("timestamp", LocalDateTime.now().toString());
            saldo.put("ambiente", "SANDBOX");
            
            log.info("✅ Saldo processado da API: {}", saldo);
            
        } catch (Exception e) {
            log.error("❌ Erro ao processar resposta de saldo: {}", e.getMessage(), e);
        }
        
        return saldo;
    }
    
    
    /**
     * Verifica se o token é temporário
     */
    private boolean isTemporaryToken(String accessToken) {
        if (accessToken == null) return true;
        
        return accessToken.contains("FIXED_TOKEN") || 
               accessToken.contains("SIMULATED") ||
               accessToken.contains("TEST_TOKEN") ||
               accessToken.contains("FAKE_TOKEN") ||
               accessToken.contains("MOCK_TOKEN") ||
               (accessToken.startsWith("TEMPORARY_AUTH_") && accessToken.length() < 100) ||
               (accessToken.length() < 50 && !accessToken.startsWith("APP_USR_"));
    }
    
    /**
     * Busca informações da conta do usuário
     */
    public Map<String, Object> buscarInformacoesConta(Long usuarioId) {
        try {
            log.info("👤 Buscando informações da conta para usuário: {}", usuarioId);
            
            // Buscar autorização bancária
            Optional<AutorizacaoBancaria> authOpt = autorizacaoBancariaRepository
                .findByUsuarioIdAndTipoBanco(usuarioId, "MERCADO_PAGO");
            
            if (!authOpt.isPresent()) {
                Map<String, Object> response = new HashMap<>();
                response.put("erro", "Usuário não possui autorização bancária do Mercado Pago");
                return response;
            }
            
            AutorizacaoBancaria auth = authOpt.get();
            String accessToken = auth.getAccessToken();
            
            if (isTemporaryToken(accessToken)) {
                Map<String, Object> response = new HashMap<>();
                response.put("erro", "Token temporário detectado");
                return response;
            }
            
            // Buscar informações da conta
            String url = "https://api.mercadopago.com/v1/users/me";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            headers.set("Content-Type", "application/json");
            
            HttpEntity<String> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> responseBody = response.getBody();
                if (responseBody != null) {
                    Map<String, Object> conta = new HashMap<>();
                    conta.put("usuario_id", responseBody.get("id"));
                    conta.put("email", responseBody.get("email"));
                    conta.put("nome", responseBody.get("first_name") + " " + responseBody.get("last_name"));
                    conta.put("telefone", responseBody.get("phone"));
                    conta.put("identificacao", responseBody.get("identification"));
                    conta.put("endereco", responseBody.get("address"));
                    conta.put("data_criacao", responseBody.get("date_created"));
                    conta.put("ambiente", "SANDBOX");
                    
                    log.info("✅ Informações da conta obtidas com sucesso");
                    return conta;
                }
            }
            
            log.warn("⚠️ Falha ao buscar informações da conta");
            return Map.of("erro", "Falha ao buscar informações da conta");
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar informações da conta: {}", e.getMessage(), e);
            return Map.of("erro", "Erro interno: " + e.getMessage());
        }
    }
}
