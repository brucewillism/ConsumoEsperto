package com.consumoesperto.service;

import com.consumoesperto.dto.*;
import com.consumoesperto.model.AutorizacaoBancaria;
import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.Fatura;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class InterBankService {

    @Value("${bank.api.inter.client-id:inter_dev_client_id}")
    private String clientId;

    @Value("${bank.api.inter.client-secret:inter_dev_client_secret}")
    private String clientSecret;

    @Value("${bank.api.inter.api-url:https://cdp.openbanking.bancointer.com.br/sandbox}")
    private String baseUrl;

    private final RestTemplate restTemplate;

    public InterBankService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String generateAuthUrl(String redirectUri, String state) {
        log.info("Gerando URL de autorização para Inter");
        
        return UriComponentsBuilder.fromHttpUrl(baseUrl + "/oauth/authorize")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "openid accounts credit-cards-accounts")
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    public Map<String, Object> processOAuthCallback(String code, String state, String redirectUri) {
        log.info("Processando callback OAuth para Inter");
        
        try {
            // Preparar headers para troca do código por token
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(clientId, clientSecret);

            // Preparar body da requisição
            String body = String.format("grant_type=authorization_code&code=%s&redirect_uri=%s", 
                    code, redirectUri);

            HttpEntity<String> request = new HttpEntity<>(body, headers);

            // Fazer chamada para trocar código por token
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/oauth/token", 
                    request, 
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> tokenData = response.getBody();
                log.info("Token obtido com sucesso para Inter");
                return tokenData;
            } else {
                log.error("Erro ao obter token do Inter: {}", response.getStatusCode());
                throw new RuntimeException("Falha na autenticação com Inter");
            }

        } catch (Exception e) {
            log.error("Erro ao processar callback OAuth do Inter: {}", e.getMessage());
            throw new RuntimeException("Erro na autenticação com Inter", e);
        }
    }





    public boolean testConnection(AutorizacaoBancaria auth) {
        log.info("Testando conexão com Inter para usuário {}", auth.getUsuario().getId());
        
        try {
            // Preparar headers com token de acesso
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(auth.getAccessToken());
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(headers);

            // Teste real de conexão com Inter
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/open-banking/v1/accounts")
                    .queryParam("access_token", auth.getAccessToken())
                    .build().toUriString();
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            boolean isConnected = response.getStatusCode() == HttpStatus.OK;
            
            log.info("Teste de conexão com Inter: {}", isConnected ? "SUCESSO" : "FALHA");
            return isConnected;
            
        } catch (Exception e) {
            log.error("Erro ao testar conexão com Inter: {}", e.getMessage());
            return false;
        }
    }

    public Map<String, Object> getBankDetails(AutorizacaoBancaria autorizacao) {
        log.info("Buscando detalhes completos do banco Inter para usuário {}", autorizacao.getUsuario().getId());
        
        try {
            // Verificar se precisa renovar o token
            if (refreshTokenIfNeeded(autorizacao)) {
                log.info("Token renovado automaticamente para Inter");
            }

            // Buscar dados de diferentes endpoints
            List<Map<String, Object>> cartoes = getCreditCards(autorizacao);
            Map<String, Object> saldo = getBalanceData(autorizacao);
            List<Map<String, Object>> faturas = getInvoices(autorizacao);
            List<Map<String, Object>> transacoes = getTransactions(autorizacao);
            Map<String, Object> gastosPorCategoria = getSpendingByCategory(autorizacao);
            Map<String, Object> analiseGastos = getSpendingAnalysis(autorizacao);

            return Map.of(
                "status", "success",
                "banco", "INTER",
                "cartoes", cartoes,
                "saldo", saldo,
                "faturas", faturas,
                "transacoes", transacoes,
                "gastosPorCategoria", gastosPorCategoria,
                "analiseGastos", analiseGastos,
                "ultimaSincronizacao", LocalDateTime.now()
            );
            
        } catch (Exception e) {
            log.error("Erro ao buscar detalhes do banco Inter: {}", e.getMessage());
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    public List<Map<String, Object>> getCreditCards(AutorizacaoBancaria autorizacao) {
        log.info("Buscando cartões de crédito do Inter para usuário {}", autorizacao.getUsuario().getId());
        
        try {
            // Preparar headers com token de acesso
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(autorizacao.getAccessToken());
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(headers);

            // Chamada real para API do Inter Open Banking
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/open-banking/v1/credit-cards-accounts")
                    .queryParam("access_token", autorizacao.getAccessToken())
                    .build().toUriString();
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Cartões obtidos com sucesso da API do Inter");
                List<CartaoCredito> cartoes = processarCartoesResposta(response.getBody());
                return cartoes.stream()
                    .map(cartao -> {
                        Map<String, Object> cartaoMap = new HashMap<>();
                        cartaoMap.put("id", cartao.getId());
                        cartaoMap.put("numero", cartao.getNumeroCartao());
                        cartaoMap.put("limite", cartao.getLimiteCredito());
                        cartaoMap.put("saldoDisponivel", cartao.getLimiteDisponivel());
                        return cartaoMap;
                    })
                    .collect(Collectors.toList());
            }
            
            log.warn("⚠️ API retornou status não esperado: {}. Nenhum cartão encontrado", response.getStatusCode());
            return new ArrayList<>();
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar cartões de crédito do Inter: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public Map<String, Object> getBalanceData(AutorizacaoBancaria autorizacao) {
        log.info("Buscando dados de saldo do Inter para usuário {}", autorizacao.getUsuario().getId());
        
        try {
            // Preparar headers com token de acesso
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(autorizacao.getAccessToken());
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(headers);

            // Chamada real para API do Inter Open Banking
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/open-banking/v1/accounts/balances")
                    .queryParam("access_token", autorizacao.getAccessToken())
                    .build().toUriString();
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Saldo obtido com sucesso da API do Inter");
                return processarSaldoResposta(response.getBody());
            }
            
            log.warn("⚠️ API retornou status não esperado: {}. Nenhum saldo encontrado", response.getStatusCode());
            return new HashMap<>();
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar dados de saldo do Inter: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    public List<Map<String, Object>> getInvoices(AutorizacaoBancaria autorizacao) {
        log.info("Buscando faturas do Inter para usuário {}", autorizacao.getUsuario().getId());
        
        try {
            // Preparar headers com token de acesso
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(autorizacao.getAccessToken());
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(headers);

            // TODO: Implementar chamada real para API do Inter
            // String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/accounts/" + autorizacao.getUsuario().getId() + "/invoices")
            //         .build().toUriString();
            // ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            // return processarFaturasResposta(response.getBody());

            // TODO: Implementar chamada real para API do Inter
            log.warn("⚠️ Endpoint de faturas não implementado. Nenhuma fatura encontrada");
            return new ArrayList<>();
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar faturas do Inter: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<Map<String, Object>> getTransactions(AutorizacaoBancaria autorizacao) {
        log.info("Buscando transações do Inter para usuário {}", autorizacao.getUsuario().getId());
        
        try {
            // Preparar headers com token de acesso
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(autorizacao.getAccessToken());
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(headers);

            // Chamada real para API do Inter Open Banking
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/open-banking/v1/accounts/transactions")
                    .queryParam("access_token", autorizacao.getAccessToken())
                    .queryParam("limit", "100")
                    .queryParam("offset", "0")
                    .build().toUriString();
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Transações obtidas com sucesso da API do Inter");
                return processarTransacoesResposta(response.getBody());
            }
            
            log.warn("⚠️ API retornou status não esperado: {}. Nenhuma transação encontrada", response.getStatusCode());
            return new ArrayList<>();
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar transações do Inter: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public Map<String, Object> getSpendingByCategory(AutorizacaoBancaria autorizacao) {
        log.info("Buscando gastos por categoria do Inter para usuário {}", autorizacao.getUsuario().getId());
        
        try {
            // Preparar headers com token de acesso
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(autorizacao.getAccessToken());
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(headers);

            // Chamada real para API do Inter Open Banking
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/open-banking/v1/accounts/spending/category")
                    .queryParam("access_token", autorizacao.getAccessToken())
                    .build().toUriString();
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Gastos por categoria obtidos com sucesso da API do Inter");
                return processarGastosPorCategoriaResposta(response.getBody());
            }
            
            log.warn("⚠️ API retornou status não esperado: {}. Nenhum dado de gastos encontrado", response.getStatusCode());
            return new HashMap<>();
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar gastos por categoria do Inter: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    public Map<String, Object> getSpendingAnalysis(AutorizacaoBancaria autorizacao) {
        log.info("Buscando análise de gastos do Inter para usuário {}", autorizacao.getUsuario().getId());
        
        try {
            // Preparar headers com token de acesso
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(autorizacao.getAccessToken());
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(headers);

            // Chamada real para API do Inter Open Banking
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/open-banking/v1/accounts/spending/analysis")
                    .queryParam("access_token", autorizacao.getAccessToken())
                    .build().toUriString();
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Análise de gastos obtida com sucesso da API do Inter");
                return processarAnaliseGastosResposta(response.getBody());
            }
            
            log.warn("⚠️ API retornou status não esperado: {}. Nenhuma análise encontrada", response.getStatusCode());
            return new HashMap<>();
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar análise de gastos do Inter: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    public boolean refreshTokenIfNeeded(AutorizacaoBancaria autorizacao) {
        log.info("Verificando se precisa renovar token do Inter para usuário {}", autorizacao.getUsuario().getId());
        
        if (!autorizacao.isTokenExpirado()) {
            log.info("Token do Inter ainda é válido");
            return false;
        }

        try {
            // Preparar headers para renovação do token
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(clientId, clientSecret);

            // Preparar body da requisição
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "refresh_token");
            body.add("refresh_token", autorizacao.getRefreshToken());

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            // TODO: Implementar chamada real para renovar token
            // ResponseEntity<Map> response = restTemplate.postForEntity(
            //         baseUrl + "/oauth/token", 
            //         request, 
            //         Map.class
            // );
            // 
            // if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            //     Map<String, Object> tokenData = response.getBody();
            //     // Atualizar a autorização com o novo token
            //     autorizacao.setAccessToken((String) tokenData.get("access_token"));
            //     autorizacao.setRefreshToken((String) tokenData.get("refresh_token"));
            //     autorizacao.setTokenExpiration(LocalDateTime.now().plusSeconds(
            //             Long.parseLong(tokenData.get("expires_in").toString())
            //     ));
            //     return true;
            // }

            // Por enquanto, retorna false (simulado)
            return false;
            
        } catch (Exception e) {
            log.error("Erro ao renovar token do Inter: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Obtém cartões de crédito do Inter
     * 
     * @param autorizacao Autorização bancária do usuário
     * @return Lista de cartões de crédito
     */
    
    // Métodos de processamento de resposta da API
    private List<CartaoCredito> processarCartoesResposta(Object response) {
        // TODO: Implementar processamento real da resposta da API
        log.info("Processando resposta de cartões da API do Inter");
        return new ArrayList<>();
    }
    
    private Map<String, Object> processarSaldoResposta(Object response) {
        // TODO: Implementar processamento real da resposta da API
        log.info("Processando resposta de saldo da API do Inter");
        return new HashMap<>();
    }
    
    private List<FaturaDTO> processarFaturasResposta(Object response) {
        // TODO: Implementar processamento real da resposta da API
        log.info("Processando resposta de faturas da API do Inter");
        return new ArrayList<>();
    }
    
    private List<Map<String, Object>> processarTransacoesResposta(Object response) {
        // TODO: Implementar processamento real da resposta da API
        log.info("Processando resposta de transações da API do Inter");
        return new ArrayList<>();
    }
    
    private Map<String, Object> processarGastosPorCategoriaResposta(Object response) {
        // TODO: Implementar processamento real da resposta da API
        log.info("Processando resposta de gastos por categoria da API do Inter");
        return new HashMap<>();
    }
    
    private Map<String, Object> processarAnaliseGastosResposta(Object response) {
        // TODO: Implementar processamento real da resposta da API
        log.info("Processando resposta de análise de gastos da API do Inter");
        return new HashMap<>();
    }
    
}
