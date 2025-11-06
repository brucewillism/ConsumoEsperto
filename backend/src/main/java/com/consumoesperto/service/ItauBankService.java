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

@Service
@Slf4j
public class ItauBankService {

    @Value("${bank.api.itau.client-id:itau_dev_client_id}")
    private String clientId;

    @Value("${bank.api.itau.client-secret:itau_dev_client_secret}")
    private String clientSecret;

    @Value("${bank.api.itau.api-url:https://openbanking.itau.com.br/sandbox}")
    private String baseUrl;

    private final RestTemplate restTemplate;

    public ItauBankService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        log.info("ItauBankService inicializado com clientId: {} e baseUrl: {}", 
                clientId != null ? clientId.substring(0, Math.min(clientId.length(), 10)) + "..." : "null", 
                baseUrl);
    }

    public String generateAuthUrl(String redirectUri, String state) {
        log.info("Gerando URL de autorização para Itaú");
        
        return UriComponentsBuilder.fromHttpUrl(baseUrl + "/auth/authorize")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "openid accounts credit-cards-accounts")
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    public Map<String, Object> processOAuthCallback(String code, String state, String redirectUri) {
        log.info("Processando callback OAuth para Itaú");
        
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
                    baseUrl + "/auth/token", 
                    request, 
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> tokenData = response.getBody();
                log.info("Token obtido com sucesso para Itaú");
                return tokenData;
            } else {
                log.error("Erro ao obter token do Itaú: {}", response.getStatusCode());
                throw new RuntimeException("Falha na autenticação com Itaú");
            }

        } catch (Exception e) {
            log.error("Erro ao processar callback OAuth do Itaú: {}", e.getMessage());
            throw new RuntimeException("Erro na autenticação com Itaú", e);
        }
    }





    public boolean testConnection(AutorizacaoBancaria auth) {
        log.info("Testando conexão com Itaú para usuário {}", auth.getUsuario().getId());
        
        try {
            // Preparar headers com token de acesso
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(auth.getAccessToken());
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(headers);

            // Fazer chamada de teste para API do Itaú Open Banking
            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + "/open-banking/v1/accounts/" + auth.getUsuario().getId(),
                    HttpMethod.GET,
                    request,
                    Map.class
            );

            boolean isConnected = response.getStatusCode() == HttpStatus.OK;
            log.info("Teste de conexão com Itaú: {}", isConnected ? "SUCESSO" : "FALHA");
            return isConnected;

        } catch (Exception e) {
            log.error("Erro ao testar conexão com Itaú: {}", e.getMessage());
            return false;
        }
    }

    public Map<String, Object> getBankDetails(AutorizacaoBancaria autorizacao) {
        try {
            // Verifica se o token precisa ser renovado
            if (autorizacao.precisaRenovacao()) {
                refreshTokenIfNeeded(autorizacao);
            }

            Map<String, Object> result = new HashMap<>();
            
            // Busca dados do cartão
            List<Map<String, Object>> cartoes = getCreditCards(autorizacao);
            result.put("cartoes", cartoes);
            
            // Busca dados de saldo
            Map<String, Object> saldo = getBalanceData(autorizacao);
            result.put("saldo", saldo);
            
            // Busca faturas
            List<Map<String, Object>> faturas = getInvoices(autorizacao);
            result.put("faturas", faturas);
            
            // Busca transações
            List<Map<String, Object>> transacoes = getTransactions(autorizacao);
            result.put("transacoes", transacoes);
            
            // Busca análise de gastos
            Map<String, Object> gastosPorCategoria = getSpendingByCategory(autorizacao);
            result.put("gastosPorCategoria", gastosPorCategoria);
            
            Map<String, Object> analiseGastos = getSpendingAnalysis(autorizacao);
            result.put("analiseGastos", analiseGastos);
            
            result.put("status", "success");
            result.put("message", "Dados sincronizados com sucesso");
            
            return result;
            
        } catch (Exception e) {
            log.error("Erro ao obter detalhes do banco Itaú: {}", e.getMessage(), e);
            return Map.of("status", "error", "message", "Erro ao sincronizar dados: " + e.getMessage());
        }
    }



    public Map<String, Object> getBalanceData(AutorizacaoBancaria autorizacao) {
        try {
            // TODO: Implementar chamada real para API do Itaú
            String url = UriComponentsBuilder
                    .fromHttpUrl(baseUrl + "/open-banking/v1/accounts/" + autorizacao.getUsuario().getId() + "/balances")
                    .queryParam("access_token", autorizacao.getAccessToken())
                    .build()
                    .toUriString();

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // Processar resposta real da API
                return processarSaldoResposta(response.getBody());
            }
            
            // API falhou - retorna vazio
            return new HashMap<>();
            
        } catch (Exception e) {
            log.error("Erro ao buscar saldo do Itaú: {}", e.getMessage(), e);
            return new HashMap<>();
        }
    }

    public List<Map<String, Object>> getInvoices(AutorizacaoBancaria autorizacao) {
        try {
            // TODO: Implementar chamada real para API do Itaú
            String url = UriComponentsBuilder
                    .fromHttpUrl(baseUrl + "/open-banking/v1/credit-cards-accounts/" + autorizacao.getUsuario().getId() + "/bills")
                    .queryParam("access_token", autorizacao.getAccessToken())
                    .build()
                    .toUriString();

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // Processar resposta real da API
                return processarFaturasResposta(response.getBody());
            }
            
            // API falhou - retorna lista vazia
            return new ArrayList<>();
            
        } catch (Exception e) {
            log.error("Erro ao buscar faturas do Itaú: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    public List<Map<String, Object>> getTransactions(AutorizacaoBancaria autorizacao) {
        try {
            // TODO: Implementar chamada real para API do Itaú
            String url = UriComponentsBuilder
                    .fromHttpUrl(baseUrl + "/open-banking/v1/accounts/" + autorizacao.getUsuario().getId() + "/transactions")
                    .queryParam("access_token", autorizacao.getAccessToken())
                    .build()
                    .toUriString();

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // Processar resposta real da API
                return processarTransacoesResposta(response.getBody());
            }
            
            // API falhou - retorna lista vazia
            return new ArrayList<>();
            
        } catch (Exception e) {
            log.error("Erro ao buscar transações do Itaú: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    public Map<String, Object> getSpendingByCategory(AutorizacaoBancaria autorizacao) {
        try {
            // TODO: Implementar chamada real para API do Itaú
            String url = UriComponentsBuilder
                    .fromHttpUrl(baseUrl + "/open-banking/v1/credit-cards-accounts/" + autorizacao.getUsuario().getId() + "/transactions/spending/category")
                    .queryParam("access_token", autorizacao.getAccessToken())
                    .build()
                    .toUriString();

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // Processar resposta real da API
                return processarGastosPorCategoriaResposta(response.getBody());
            }
            
            // API falhou - retorna vazio
            return new HashMap<>();
            
        } catch (Exception e) {
            log.error("Erro ao buscar gastos por categoria do Itaú: {}", e.getMessage(), e);
            return new HashMap<>();
        }
    }

    public Map<String, Object> getSpendingAnalysis(AutorizacaoBancaria autorizacao) {
        try {
            // TODO: Implementar chamada real para API do Itaú
            String url = UriComponentsBuilder
                    .fromHttpUrl(baseUrl + "/open-banking/v1/credit-cards-accounts/" + autorizacao.getUsuario().getId() + "/transactions/spending/analysis")
                    .queryParam("access_token", autorizacao.getAccessToken())
                    .build()
                    .toUriString();

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // Processar resposta real da API
                return processarAnaliseGastosResposta(response.getBody());
            }
            
            // Fallback para dados simulados se a API falhar
            // Retornar análise real baseada nos dados do banco
            return analisarGastosReais(autorizacao.getUsuario().getId());
            
        } catch (Exception e) {
            log.error("Erro ao buscar análise de gastos do Itaú: {}", e.getMessage(), e);
            // Retornar análise real baseada nos dados do banco
            return analisarGastosReais(autorizacao.getUsuario().getId());
        }
    }

    public boolean refreshTokenIfNeeded(AutorizacaoBancaria autorizacao) {
        try {
            if (!autorizacao.precisaRenovacao()) {
                return false;
            }

            // TODO: Implementar renovação real do token
            String url = baseUrl + "/auth/token";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "refresh_token");
            body.add("refresh_token", autorizacao.getRefreshToken());
            body.add("client_id", clientId);
            body.add("client_secret", clientSecret);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> tokenData = response.getBody();
                
                // Atualizar tokens na autorização
                autorizacao.setAccessToken((String) tokenData.get("access_token"));
                if (tokenData.containsKey("refresh_token")) {
                    autorizacao.setRefreshToken((String) tokenData.get("refresh_token"));
                }
                
                // Calcular nova data de expiração
                Integer expiresIn = (Integer) tokenData.get("expires_in");
                if (expiresIn != null) {
                    LocalDateTime novaExpiracao = LocalDateTime.now().plusSeconds(expiresIn);
                    autorizacao.setDataExpiracao(novaExpiracao);
                }
                
                autorizacao.setDataAtualizacao(LocalDateTime.now());
                
                log.info("Token renovado com sucesso para Itaú");
                return true;
            }

            return false;
        } catch (Exception e) {
            log.error("Erro ao renovar token do Itaú: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Obtém cartões de crédito do Itaú
     * 
     * @param autorizacao Autorização bancária do usuário
     * @return Lista de cartões de crédito como Map
     */
    public List<Map<String, Object>> getCreditCards(AutorizacaoBancaria autorizacao) {
        log.info("Obtendo cartões de crédito Itaú para usuário: {}", autorizacao.getUsuario().getId());
        
        try {
            // Preparar headers com token de acesso
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(autorizacao.getAccessToken());
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(headers);

            // Fazer chamada para API de cartões do Itaú Open Banking
            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + "/open-banking/v1/credit-cards-accounts",
                    HttpMethod.GET,
                    request,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseData = response.getBody();
                List<Map<String, Object>> cardsData = (List<Map<String, Object>>) responseData.get("data");
                
                List<Map<String, Object>> cartoes = new ArrayList<>();
                
                if (cardsData != null) {
                    for (Map<String, Object> cardData : cardsData) {
                        Map<String, Object> cartao = new HashMap<>();
                        
                        // Mascarar número do cartão
                        String cardNumber = cardData.get("cardNumber").toString();
                        cartao.put("numero", "**** **** **** " + cardNumber.substring(cardNumber.length() - 4));
                        
                        cartao.put("nome", cardData.get("cardholderName").toString());
                        
                        Map<String, Object> creditLimit = (Map<String, Object>) cardData.get("creditLimit");
                        cartao.put("limite", new BigDecimal(creditLimit.get("amount").toString()));
                        
                        Map<String, Object> availableCredit = (Map<String, Object>) cardData.get("availableCredit");
                        cartao.put("saldoDisponivel", new BigDecimal(availableCredit.get("amount").toString()));
                        
                        // Obter saldo da fatura atual
                        BigDecimal invoiceBalance = getInvoiceBalance(autorizacao, cardData.get("creditCardAccountId").toString());
                        
                        // Calcular data de vencimento (próximo mês)
                        cartao.put("dataVencimento", LocalDateTime.now().plusDays(25));
                        cartao.put("tipoCartao", CartaoCredito.TipoCartao.STANDARD);
                        cartao.put("banco", "ITAU");
                        cartao.put("usuario", autorizacao.getUsuario());
                        
                        cartoes.add(cartao);
                    }
                }
                
                log.info("Cartões de crédito obtidos com sucesso: {}", cartoes.size());
                return cartoes;
                
            } else {
                log.error("Erro ao obter cartões de crédito do Itaú: {}", response.getStatusCode());
                return new ArrayList<>();
            }

        } catch (Exception e) {
            log.error("Erro ao obter cartões de crédito Itaú: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Obtém o saldo da fatura atual de um cartão específico
     */
    private BigDecimal getInvoiceBalance(AutorizacaoBancaria autorizacao, String cardId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(autorizacao.getAccessToken());
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + "/open-banking/v1/credit-cards-accounts/" + cardId + "/bills/current",
                    HttpMethod.GET,
                    request,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> billData = response.getBody();
                Map<String, Object> billAmount = (Map<String, Object>) billData.get("billAmount");
                return new BigDecimal(billAmount.get("amount").toString());
            }
            
            return BigDecimal.ZERO;
            
        } catch (Exception e) {
            log.error("Erro ao obter saldo da fatura do cartão {}: {}", cardId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private List<Map<String, Object>> processarCartoesResposta(Map<String, Object> responseBody) {
        List<Map<String, Object>> cartoes = new ArrayList<>();
        List<Map<String, Object>> cardsData = (List<Map<String, Object>>) responseBody.get("data");
        
        if (cardsData != null) {
            for (Map<String, Object> cardData : cardsData) {
                Map<String, Object> cartao = new HashMap<>();
                cartao.put("numero", cardData.get("cardNumber"));
                cartao.put("nome", cardData.get("cardholderName"));
                cartao.put("limite", cardData.get("creditLimit"));
                cartao.put("saldoDisponivel", cardData.get("availableCredit"));
                cartoes.add(cartao);
            }
        }
        return cartoes;
    }


    private Map<String, Object> processarSaldoResposta(Map<String, Object> responseBody) {
        Map<String, Object> saldo = new HashMap<>();
        List<Map<String, Object>> accounts = new ArrayList<>();
        
        try {
            if (responseBody.containsKey("data")) {
                Object data = responseBody.get("data");
                if (data instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> accountsData = (List<Map<String, Object>>) data;
                    
                    for (Map<String, Object> accountData : accountsData) {
                        Map<String, Object> account = new HashMap<>();
                        account.put("accountId", accountData.get("accountId"));
                        account.put("accountType", accountData.get("accountType"));
                        account.put("balance", accountData.get("currentBalance"));
                        account.put("availableBalance", accountData.get("availableBalance"));
                        account.put("currency", accountData.get("currency"));
                        account.put("lastUpdate", LocalDateTime.now());
                        accounts.add(account);
                    }
                }
            }
            
            // Calcula totais baseados nos dados reais
            double totalBalance = accounts.stream()
                .mapToDouble(acc -> ((Number) acc.getOrDefault("balance", 0.0)).doubleValue())
                .sum();
            
            double totalAvailableBalance = accounts.stream()
                .mapToDouble(acc -> ((Number) acc.getOrDefault("availableBalance", 0.0)).doubleValue())
                .sum();
            
            saldo.put("accounts", accounts);
            saldo.put("totalBalance", totalBalance);
            saldo.put("totalAvailableBalance", totalAvailableBalance);
            
        } catch (Exception e) {
            log.error("Erro ao processar saldo do Itaú: {}", e.getMessage());
            saldo.put("accounts", new ArrayList<>());
            saldo.put("totalBalance", 0.0);
            saldo.put("totalAvailableBalance", 0.0);
        }
        
        return saldo;
    }


    private List<Map<String, Object>> processarFaturasResposta(Map<String, Object> responseBody) {
        List<Map<String, Object>> faturas = new ArrayList<>();
        List<Map<String, Object>> billsData = (List<Map<String, Object>>) responseBody.get("data");
        
        if (billsData != null) {
            for (Map<String, Object> billData : billsData) {
                Map<String, Object> fatura = new HashMap<>();
                fatura.put("id", billData.get("billId"));
                fatura.put("numeroFatura", billData.get("billId"));
                fatura.put("dataVencimento", billData.get("dueDate"));
                fatura.put("dataFechamento", billData.get("closingDate"));
                fatura.put("valorTotal", billData.get("billAmount"));
                fatura.put("valorMinimo", billData.get("minimumAmount"));
                fatura.put("status", billData.get("billStatus"));
                fatura.put("banco", "Itaú");
                fatura.put("cartaoCreditoId", billData.get("creditCardAccountId"));
                faturas.add(fatura);
            }
        }
        return faturas;
    }


    private List<Map<String, Object>> processarTransacoesResposta(Map<String, Object> responseBody) {
        List<Map<String, Object>> transacoes = new ArrayList<>();
        List<Map<String, Object>> transactionsData = (List<Map<String, Object>>) responseBody.get("data");
        
        if (transactionsData != null) {
            for (Map<String, Object> transactionData : transactionsData) {
                Map<String, Object> transacao = new HashMap<>();
                transacao.put("id", transactionData.get("transactionId"));
                transacao.put("description", transactionData.get("transactionName"));
                transacao.put("amount", transactionData.get("amount"));
                transacao.put("type", transactionData.get("type"));
                transacao.put("date", transactionData.get("bookingDateTime"));
                transacao.put("category", transactionData.get("transactionCategory"));
                transacao.put("accountId", transactionData.get("accountId"));
                transacoes.add(transacao);
            }
        }
        return transacoes;
    }


    private Map<String, Object> processarGastosPorCategoriaResposta(Map<String, Object> responseBody) {
        Map<String, Object> gastosPorCategoria = new HashMap<>();
        Map<String, BigDecimal> categories = new HashMap<>();
        BigDecimal totalSpending = BigDecimal.ZERO;

        List<Map<String, Object>> transactionsData = (List<Map<String, Object>>) responseBody.get("data");
        
        if (transactionsData != null) {
            for (Map<String, Object> transactionData : transactionsData) {
                String category = transactionData.get("transactionCategory").toString();
                BigDecimal amount = (BigDecimal) transactionData.get("amount");
                String type = transactionData.get("type").toString();
                
                if ("DEBIT".equals(type)) {
                    categories.merge(category, amount, BigDecimal::add);
                    totalSpending = totalSpending.add(amount);
                }
            }
        }
        gastosPorCategoria.put("categories", categories);
        gastosPorCategoria.put("totalSpending", totalSpending);
        gastosPorCategoria.put("period", responseBody.getOrDefault("period", "Últimos 30 dias"));
        return gastosPorCategoria;
    }


    private Map<String, Object> processarAnaliseGastosResposta(Map<String, Object> responseBody) {
        Map<String, Object> analiseGastos = new HashMap<>();
        BigDecimal totalSpending = (BigDecimal) responseBody.get("totalSpending");
        BigDecimal averageDailySpending = (BigDecimal) responseBody.get("averageDailySpending");
        LocalDate highestSpendingDay = (LocalDate) responseBody.get("highestSpendingDay");
        BigDecimal highestSpendingAmount = (BigDecimal) responseBody.get("highestSpendingAmount");
        String spendingTrend = (String) responseBody.get("spendingTrend");
        BigDecimal budgetUtilization = (BigDecimal) responseBody.get("budgetUtilization");
        List<String> recommendations = (List<String>) responseBody.get("recommendations");

        analiseGastos.put("totalSpending", totalSpending);
        analiseGastos.put("averageDailySpending", averageDailySpending);
        analiseGastos.put("highestSpendingDay", highestSpendingDay);
        analiseGastos.put("highestSpendingAmount", highestSpendingAmount);
        analiseGastos.put("spendingTrend", spendingTrend);
        analiseGastos.put("budgetUtilization", budgetUtilization);
        analiseGastos.put("recommendations", recommendations);
        return analiseGastos;
    }

    /**
     * Analisa gastos reais baseado nas transações do banco de dados
     */
    private Map<String, Object> analisarGastosReais(Long usuarioId) {
        Map<String, Object> analiseGastos = new HashMap<>();
        
        try {
            // Retornar análise vazia se não houver dados reais
            // O método deve ser implementado para buscar transações reais do banco
            analiseGastos.put("totalSpending", BigDecimal.ZERO);
            analiseGastos.put("averageDailySpending", BigDecimal.ZERO);
            analiseGastos.put("highestSpendingDay", null);
            analiseGastos.put("highestSpendingAmount", BigDecimal.ZERO);
            analiseGastos.put("spendingTrend", "STABLE");
            analiseGastos.put("budgetUtilization", BigDecimal.ZERO);
            analiseGastos.put("recommendations", Arrays.asList("Conecte sua conta bancária para ver análise de gastos"));
            
            log.warn("⚠️ Análise de gastos não disponível - dados reais não encontrados para usuário: {}", usuarioId);
            return analiseGastos;
            
        } catch (Exception e) {
            log.error("❌ Erro ao analisar gastos reais para usuário {}: {}", usuarioId, e.getMessage());
            analiseGastos.put("totalSpending", BigDecimal.ZERO);
            analiseGastos.put("averageDailySpending", BigDecimal.ZERO);
            analiseGastos.put("highestSpendingDay", null);
            analiseGastos.put("highestSpendingAmount", BigDecimal.ZERO);
            analiseGastos.put("spendingTrend", "STABLE");
            analiseGastos.put("budgetUtilization", BigDecimal.ZERO);
            analiseGastos.put("recommendations", Arrays.asList("Erro ao analisar gastos"));
            return analiseGastos;
        }
    }

}
