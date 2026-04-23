package com.consumoesperto.service;

import com.consumoesperto.dto.*;
import com.consumoesperto.model.AutorizacaoBancaria;
import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.Fatura;
import com.consumoesperto.repository.AutorizacaoBancariaRepository;
import lombok.RequiredArgsConstructor;
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

@Service
@Slf4j
public class NubankBankService {

    @Value("${bank.api.nubank.client-id:nubank_dev_client_id}")
    private String clientId;

    @Value("${bank.api.nubank.client-secret:nubank_dev_client_secret}")
    private String clientSecret;

    @Value("${bank.api.nubank.api-url:https://api.nubank.com.br}")
    private String baseUrl;

    private final RestTemplate restTemplate;
    private final AutorizacaoBancariaRepository autorizacaoBancariaRepository;

    public NubankBankService(RestTemplate restTemplate, AutorizacaoBancariaRepository autorizacaoBancariaRepository) {
        this.restTemplate = restTemplate;
        this.autorizacaoBancariaRepository = autorizacaoBancariaRepository;
    }

    public String generateAuthUrl(String redirectUri, String state) {
        log.info("Gerando URL de autorização para Nubank");
        
        return UriComponentsBuilder.fromHttpUrl(baseUrl + "/oauth/authorize")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "read")
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    public Map<String, Object> processOAuthCallback(String code, String state, String redirectUri) {
        log.info("Processando callback OAuth para Nubank");
        
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
                log.info("Token obtido com sucesso para Nubank");
                return tokenData;
            } else {
                log.error("Erro ao obter token do Nubank: {}", response.getStatusCode());
                throw new RuntimeException("Falha na autenticação com Nubank");
            }

        } catch (Exception e) {
            log.error("Erro ao processar callback OAuth do Nubank: {}", e.getMessage());
            throw new RuntimeException("Erro na autenticação com Nubank", e);
        }
    }



    public boolean testConnection(AutorizacaoBancaria auth) {
        log.info("Testando conexão com Nubank para usuário {}", auth.getUsuario().getId());
        
        try {
            // Preparar headers com token de acesso
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(auth.getAccessToken());
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(headers);

            // Implementar teste real de conexão
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/accounts")
                    .build().toUriString();
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            return response.getStatusCode() == HttpStatus.OK;
            
        } catch (Exception e) {
            log.error("Erro ao testar conexão com Nubank: {}", e.getMessage());
            return false;
        }
    }

    public Map<String, Object> getBankDetails(AutorizacaoBancaria autorizacao) {
        log.info("Buscando detalhes completos do banco Nubank para usuário {}", autorizacao.getUsuario().getId());
        
        try {
            // Verificar se precisa renovar o token
            if (refreshTokenIfNeeded(autorizacao)) {
                log.info("Token renovado automaticamente para Nubank");
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
                "banco", "NUBANK",
                "cartoes", cartoes,
                "saldo", saldo,
                "faturas", faturas,
                "transacoes", transacoes,
                "gastosPorCategoria", gastosPorCategoria,
                "analiseGastos", analiseGastos,
                "ultimaSincronizacao", LocalDateTime.now()
            );
            
        } catch (Exception e) {
            log.error("Erro ao buscar detalhes do banco Nubank: {}", e.getMessage());
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    public List<Map<String, Object>> getCreditCards(AutorizacaoBancaria autorizacao) {
        log.info("Buscando cartões de crédito do Nubank para usuário {}", autorizacao.getUsuario().getId());
        
        try {
            // Preparar headers com token de acesso
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(autorizacao.getAccessToken());
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(headers);

            // Chamada real para API do Nubank
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/api/cards")
                    .queryParam("access_token", autorizacao.getAccessToken())
                    .build().toUriString();
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Cartões obtidos com sucesso da API do Nubank");
                return processarCartoesResposta(response.getBody());
            }
            
            log.warn("API retornou status não esperado: {}. Retornando lista vazia", response.getStatusCode());
            return new ArrayList<>();
            
        } catch (Exception e) {
            log.error("Erro ao buscar cartões de crédito do Nubank: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public Map<String, Object> getBalanceData(AutorizacaoBancaria autorizacao) {
        log.info("Buscando dados de saldo do Nubank para usuário {}", autorizacao.getUsuario().getId());
        
        try {
            // Preparar headers com token de acesso
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(autorizacao.getAccessToken());
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(headers);

            // Chamada real para API do Nubank
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/api/accounts/balance")
                    .queryParam("access_token", autorizacao.getAccessToken())
                    .build().toUriString();
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Saldo obtido com sucesso da API do Nubank");
                return processarSaldoResposta(response.getBody());
            }
            
            log.warn("API retornou status não esperado: {}", response.getStatusCode());
            return new HashMap<>();
            
        } catch (Exception e) {
            log.error("Erro ao buscar dados de saldo do Nubank: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    public List<Map<String, Object>> getInvoices(AutorizacaoBancaria autorizacao) {
        log.info("Buscando faturas do Nubank para usuário {}", autorizacao.getUsuario().getId());
        
        try {
            // Preparar headers com token de acesso
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(autorizacao.getAccessToken());
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(headers);

            // Chamada real para API do Nubank - buscar faturas
            // A API do Nubank usa o endpoint /api/bills para faturas
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/api/bills")
                    .queryParam("access_token", autorizacao.getAccessToken())
                    .build().toUriString();
            
            try {
                ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
                
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    log.info("Faturas obtidas com sucesso da API do Nubank");
                    return processarFaturasResposta(response.getBody());
                }
                
                log.warn("API retornou status não esperado: {}", response.getStatusCode());
            } catch (Exception apiException) {
                log.warn("Erro ao buscar faturas do endpoint /api/bills, tentando alternativa: {}", apiException.getMessage());
                
                // Tentar endpoint alternativo se o principal falhar
                try {
                    String altUrl = UriComponentsBuilder.fromHttpUrl(baseUrl + "/api/invoices")
                            .queryParam("access_token", autorizacao.getAccessToken())
                            .build().toUriString();
                    
                    ResponseEntity<Map> altResponse = restTemplate.exchange(altUrl, HttpMethod.GET, request, Map.class);
                    
                    if (altResponse.getStatusCode().is2xxSuccessful() && altResponse.getBody() != null) {
                        log.info("Faturas obtidas com sucesso usando endpoint alternativo");
                        return processarFaturasResposta(altResponse.getBody());
                    }
                } catch (Exception altException) {
                    log.warn("Endpoint alternativo também falhou: {}", altException.getMessage());
                }
            }
            
            return new ArrayList<>();
            
        } catch (Exception e) {
            log.error("Erro ao buscar faturas do Nubank: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<Map<String, Object>> getTransactions(AutorizacaoBancaria autorizacao) {
        log.info("Buscando transações do Nubank para usuário {}", autorizacao.getUsuario().getId());
        
        try {
            // Preparar headers com token de acesso
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(autorizacao.getAccessToken());
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(headers);

            // Chamada real para API do Nubank
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/api/transactions")
                    .queryParam("access_token", autorizacao.getAccessToken())
                    .queryParam("limit", "100")
                    .queryParam("offset", "0")
                    .build().toUriString();
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Transações obtidas com sucesso da API do Nubank");
                return processarTransacoesResposta(response.getBody());
            }
            
            log.warn("API retornou status não esperado: {}", response.getStatusCode());
            return new ArrayList<>();
            
        } catch (Exception e) {
            log.error("Erro ao buscar transações do Nubank: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public Map<String, Object> getSpendingByCategory(AutorizacaoBancaria autorizacao) {
        log.info("Buscando gastos por categoria do Nubank para usuário {}", autorizacao.getUsuario().getId());
        
        try {
            // Preparar headers com token de acesso
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(autorizacao.getAccessToken());
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(headers);

            // Como a API do Nubank pode não ter endpoint direto de gastos por categoria,
            // vamos calcular a partir das transações
            try {
                // Buscar transações primeiro
                List<Map<String, Object>> transacoes = getTransactions(autorizacao);
                
                if (transacoes == null || transacoes.isEmpty()) {
                    log.warn("Nenhuma transação encontrada para calcular gastos por categoria");
                    return new HashMap<>();
                }
                
                // Calcular gastos por categoria a partir das transações
                Map<String, Double> gastosPorCategoria = new HashMap<>();
                
                for (Map<String, Object> transacao : transacoes) {
                    String categoria = (String) transacao.getOrDefault("category", "OUTROS");
                    Object valorObj = transacao.get("amount");
                    
                    if (valorObj != null) {
                        double valor = 0.0;
                        if (valorObj instanceof Number) {
                            valor = ((Number) valorObj).doubleValue();
                        } else if (valorObj instanceof String) {
                            try {
                                valor = Double.parseDouble((String) valorObj);
                            } catch (NumberFormatException e) {
                                log.warn("Valor inválido na transação: {}", valorObj);
                                continue;
                            }
                        }
                        
                        // Considerar apenas valores negativos (gastos)
                        if (valor < 0) {
                            valor = Math.abs(valor); // Converter para positivo para soma
                            gastosPorCategoria.put(categoria, 
                                gastosPorCategoria.getOrDefault(categoria, 0.0) + valor);
                        }
                    }
                }
                
                log.info("Gastos por categoria calculados: {} categorias encontradas", gastosPorCategoria.size());
                return processarGastosPorCategoriaResposta(Map.of("categories", gastosPorCategoria));
                
            } catch (Exception e) {
                log.error("Erro ao calcular gastos por categoria do Nubank: {}", e.getMessage(), e);
                return new HashMap<>();
            }
            
        } catch (Exception e) {
            log.error("Erro ao buscar gastos por categoria do Nubank: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    public Map<String, Object> getSpendingAnalysis(AutorizacaoBancaria autorizacao) {
        log.info("Buscando análise de gastos do Nubank para usuário {}", autorizacao.getUsuario().getId());
        
        try {
            // Preparar headers com token de acesso
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(autorizacao.getAccessToken());
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(headers);

            // TODO: Implementar chamada real para API do Nubank
            // String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/accounts/" + autorizacao.getAccountId() + "/spending/analysis")
            //         .build().toUriString();
            // ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            // return processarAnaliseGastosResposta(response.getBody());

            // Endpoint não implementado - retorna vazio
            return new HashMap<>();
            
        } catch (Exception e) {
            log.error("Erro ao buscar análise de gastos do Nubank: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    public boolean refreshTokenIfNeeded(AutorizacaoBancaria autorizacao) {
        log.info("Verificando se precisa renovar token do Nubank para usuário {}", autorizacao.getUsuario().getId());
        
        if (!autorizacao.isTokenExpirado()) {
            log.info("Token do Nubank ainda é válido");
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

            // Chamada real para renovar token do Nubank
            try {
                ResponseEntity<Map> response = restTemplate.postForEntity(
                        baseUrl + "/oauth/token", 
                        request, 
                        Map.class
                );
                
                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    Map<String, Object> tokenData = response.getBody();
                    
                    // Atualizar a autorização com o novo token
                    String newAccessToken = (String) tokenData.get("access_token");
                    String newRefreshToken = (String) tokenData.getOrDefault("refresh_token", autorizacao.getRefreshToken());
                    Object expiresInObj = tokenData.get("expires_in");
                    
                    if (newAccessToken != null) {
                        autorizacao.setAccessToken(newAccessToken);
                        if (newRefreshToken != null) {
                            autorizacao.setRefreshToken(newRefreshToken);
                        }
                        
                        // Calcular nova data de expiração
                        if (expiresInObj != null) {
                            long expiresIn = 0;
                            if (expiresInObj instanceof Number) {
                                expiresIn = ((Number) expiresInObj).longValue();
                            } else if (expiresInObj instanceof String) {
                                expiresIn = Long.parseLong((String) expiresInObj);
                            }
                            
                            if (expiresIn > 0) {
                                autorizacao.setDataExpiracao(LocalDateTime.now().plusSeconds(expiresIn));
                            }
                        }
                        
                        // Salvar autorização atualizada no banco de dados
                        autorizacao.setDataAtualizacao(LocalDateTime.now());
                        autorizacaoBancariaRepository.save(autorizacao);
                        
                        log.info("Token renovado com sucesso para Nubank - usuário {}", autorizacao.getUsuario().getId());
                        return true;
                    } else {
                        log.warn("Resposta de renovação de token não contém access_token");
                        return false;
                    }
                } else {
                    log.warn("Falha ao renovar token - status: {}", response.getStatusCode());
                    return false;
                }
                
            } catch (Exception e) {
                log.error("Erro ao renovar token do Nubank: {}", e.getMessage(), e);
                return false;
            }
            
        } catch (Exception e) {
            log.error("Erro ao renovar token do Nubank: {}", e.getMessage());
            return false;
        }
    }



    private List<Map<String, Object>> processarCartoesResposta(Map<String, Object> responseBody) {
        List<Map<String, Object>> cartoes = new ArrayList<>();
        if (responseBody.containsKey("cards")) {
            List<Map<String, Object>> cardsData = (List<Map<String, Object>>) responseBody.get("cards");
            for (Map<String, Object> cardData : cardsData) {
                Map<String, Object> cartao = new HashMap<>();
                cartao.put("id", cardData.get("id"));
                cartao.put("number", cardData.get("number"));
                cartao.put("holder_name", cardData.get("holder_name"));
                cartao.put("limit", cardData.get("limit"));
                cartao.put("available_limit", cardData.get("available_limit"));
                cartoes.add(cartao);
            }
        }
        return cartoes;
    }


    private Map<String, Object> processarSaldoResposta(Map<String, Object> responseBody) {
        Map<String, Object> saldo = new HashMap<>();
        if (responseBody.containsKey("accounts")) {
            List<Map<String, Object>> accounts = (List<Map<String, Object>>) responseBody.get("accounts");
            if (!accounts.isEmpty()) {
                Map<String, Object> account = accounts.get(0);
                saldo.put("balance", account.get("balance"));
                saldo.put("availableBalance", account.get("availableBalance"));
                saldo.put("currency", account.get("currency"));
                saldo.put("lastUpdate", account.get("lastUpdate"));
            }
        }
        return saldo;
    }


    private List<Map<String, Object>> processarFaturasResposta(Map<String, Object> responseBody) {
        List<Map<String, Object>> faturas = new ArrayList<>();
        if (responseBody.containsKey("bills")) {
            List<Map<String, Object>> billsData = (List<Map<String, Object>>) responseBody.get("bills");
            for (Map<String, Object> billData : billsData) {
                Map<String, Object> fatura = new HashMap<>();
                fatura.put("id", billData.get("id"));
                fatura.put("number", billData.get("number"));
                fatura.put("due_date", billData.get("due_date"));
                fatura.put("close_date", billData.get("close_date"));
                fatura.put("total", billData.get("total"));
                fatura.put("minimum", billData.get("minimum"));
                fatura.put("state", billData.get("state"));
                fatura.put("card_id", billData.get("card_id"));
                faturas.add(fatura);
            }
        }
        return faturas;
    }


    private List<Map<String, Object>> processarTransacoesResposta(Map<String, Object> responseBody) {
        List<Map<String, Object>> transacoes = new ArrayList<>();
        if (responseBody.containsKey("transactions")) {
            List<Map<String, Object>> transactionsData = (List<Map<String, Object>>) responseBody.get("transactions");
            for (Map<String, Object> transactionData : transactionsData) {
                Map<String, Object> transacao = new HashMap<>();
                transacao.put("id", transactionData.get("id"));
                transacao.put("description", transactionData.get("description"));
                transacao.put("amount", transactionData.get("amount"));
                transacao.put("type", transactionData.get("type"));
                transacao.put("date", transactionData.get("date"));
                transacao.put("category", transactionData.get("category"));
                transacao.put("account_id", transactionData.get("accountId")); // Assuming accountId is part of transaction data
                transacoes.add(transacao);
            }
        }
        return transacoes;
    }


    private Map<String, Object> processarGastosPorCategoriaResposta(Map<String, Object> responseBody) {
        Map<String, Object> gastosPorCategoria = new HashMap<>();
        if (responseBody.containsKey("categories")) {
            Map<String, BigDecimal> categories = (Map<String, BigDecimal>) responseBody.get("categories");
            gastosPorCategoria.put("categories", categories);
            gastosPorCategoria.put("totalSpending", categories.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
            gastosPorCategoria.put("period", responseBody.getOrDefault("period", "Últimos 30 dias"));
        }
        return gastosPorCategoria;
    }


    private Map<String, Object> processarAnaliseGastosResposta(Map<String, Object> responseBody) {
        Map<String, Object> analiseGastos = new HashMap<>();
        if (responseBody.containsKey("totalSpending")) {
            analiseGastos.put("totalSpending", responseBody.get("totalSpending"));
            analiseGastos.put("averageDailySpending", responseBody.get("averageDailySpending"));
            analiseGastos.put("highestSpendingDay", responseBody.get("highestSpendingDay"));
            analiseGastos.put("highestSpendingAmount", responseBody.get("highestSpendingAmount"));
            analiseGastos.put("spendingTrend", responseBody.get("spendingTrend"));
            analiseGastos.put("budgetUtilization", responseBody.get("budgetUtilization"));
            analiseGastos.put("recommendations", responseBody.get("recommendations"));
        } else {
            analiseGastos.put("totalSpending", BigDecimal.ZERO);
            analiseGastos.put("averageDailySpending", BigDecimal.ZERO);
            analiseGastos.put("highestSpendingDay", LocalDate.now());
            analiseGastos.put("highestSpendingAmount", BigDecimal.ZERO);
            analiseGastos.put("spendingTrend", "NO_DATA");
            analiseGastos.put("budgetUtilization", BigDecimal.ZERO);
            analiseGastos.put("recommendations", Arrays.asList("Nenhum dado disponível para o período"));
        }
        return analiseGastos;
    }

}
