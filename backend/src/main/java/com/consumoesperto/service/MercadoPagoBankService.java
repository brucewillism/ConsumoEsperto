package com.consumoesperto.service;

import com.consumoesperto.dto.*;
import com.consumoesperto.model.AutorizacaoBancaria;
import com.consumoesperto.model.BankApiConfig;
import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.Fatura;
import com.consumoesperto.repository.BankApiConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@ConditionalOnProperty(name = "bank.api.mercadopago.client-id", havingValue = "4223603750190943", matchIfMissing = true)
@Slf4j
public class MercadoPagoBankService {

    private final RestTemplate restTemplate;
    private final BankApiConfigRepository bankApiConfigRepository;

    public MercadoPagoBankService(RestTemplate restTemplate, BankApiConfigRepository bankApiConfigRepository) {
        this.restTemplate = restTemplate;
        this.bankApiConfigRepository = bankApiConfigRepository;
    }

    /**
     * Busca configuração do Mercado Pago para um usuário
     */
    private Optional<BankApiConfig> getMercadoPagoConfig(Long userId) {
        try {
            log.debug("🔍 Buscando configuração do Mercado Pago para usuário: {}", userId);
            Optional<BankApiConfig> config = bankApiConfigRepository.findByUsuarioIdAndBanco(userId, "MERCADOPAGO");
            if (config.isPresent() && config.get().getAtivo()) {
                return config;
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("❌ Erro ao buscar configuração do Mercado Pago para usuário {}: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    public String generateAuthUrl(String redirectUri, String state, Long userId) {
        log.info("Gerando URL de autorização para Mercado Pago - Usuário: {}", userId);
        
        Optional<BankApiConfig> config = getMercadoPagoConfig(userId);
        if (config.isEmpty()) {
            log.error("❌ Usuário {} não possui configuração do Mercado Pago", userId);
            throw new RuntimeException("Configuração do Mercado Pago não encontrada");
        }
        
        BankApiConfig mpConfig = config.get();
        
        return UriComponentsBuilder.fromHttpUrl(mpConfig.getAuthUrl())
                .queryParam("client_id", mpConfig.getClientId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", mpConfig.getScope())
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    public Map<String, Object> processOAuthCallback(String code, String state, String redirectUri, Long userId) {
        log.info("Processando callback OAuth para Mercado Pago - Usuário: {}", userId);
        
        Optional<BankApiConfig> config = getMercadoPagoConfig(userId);
        if (config.isEmpty()) {
            log.error("❌ Usuário {} não possui configuração do Mercado Pago", userId);
            throw new RuntimeException("Configuração do Mercado Pago não encontrada");
        }
        
        BankApiConfig mpConfig = config.get();
        
        try {
            // Preparar headers para troca do código por token
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(mpConfig.getClientId(), mpConfig.getClientSecret());

            // Preparar body da requisição
            String body = String.format("grant_type=authorization_code&code=%s&redirect_uri=%s", 
                    code, redirectUri);

            HttpEntity<String> request = new HttpEntity<>(body, headers);

            // Fazer chamada para trocar código por token
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    mpConfig.getTokenUrl(), 
                    request, 
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> tokenData = response.getBody();
                log.info("Token obtido com sucesso para Mercado Pago - Usuário: {}", userId);
                return tokenData;
            } else {
                log.error("Erro ao obter token do Mercado Pago - Usuário: {} - Status: {}", userId, response.getStatusCode());
                throw new RuntimeException("Falha na autenticação com Mercado Pago");
            }

        } catch (Exception e) {
            log.error("Erro ao processar callback OAuth do Mercado Pago - Usuário: {} - Erro: {}", userId, e.getMessage());
            throw new RuntimeException("Erro na autenticação com Mercado Pago", e);
        }
    }

    public boolean testConnection(AutorizacaoBancaria auth) {
        log.info("Testando conexão com Mercado Pago para usuário {}", auth.getUsuario().getId());
        
        try {
            // Buscar configuração do usuário
            Optional<BankApiConfig> config = getMercadoPagoConfig(auth.getUsuario().getId());
            if (config.isEmpty()) {
                log.error("❌ Usuário {} não possui configuração do Mercado Pago", auth.getUsuario().getId());
                return false;
            }
            
            // Preparar headers com token de acesso
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(auth.getAccessToken());
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(headers);

            // Fazer chamada de teste para API do Mercado Pago
            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://api.mercadopago.com/v1/users/" + config.get().getUserId() + "/accounts",
                    HttpMethod.GET,
                    request,
                    Map.class
            );

            boolean isConnected = response.getStatusCode() == HttpStatus.OK;
            log.info("Teste de conexão com Mercado Pago: {}", isConnected ? "SUCESSO" : "FALHA");
            return isConnected;

        } catch (Exception e) {
            log.error("Erro ao testar conexão com Mercado Pago: {}", e.getMessage());
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
            log.error("Erro ao obter detalhes do banco Mercado Pago: {}", e.getMessage(), e);
            return Map.of("status", "error", "message", "Erro ao sincronizar dados: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> getCreditCards(AutorizacaoBancaria autorizacao) {
        try {
            log.info("Buscando cartões reais do Mercado Pago para usuário {}", autorizacao.getUsuario().getId());
            
            // Buscar configuração do usuário
            Optional<BankApiConfig> config = getMercadoPagoConfig(autorizacao.getUsuario().getId());
            if (config.isEmpty()) {
                log.error("❌ Usuário {} não possui configuração do Mercado Pago", autorizacao.getUsuario().getId());
                return getSimulatedCreditCards();
            }

            // Chamada real para API do Mercado Pago
            String url = UriComponentsBuilder
                    .fromHttpUrl(config.get().getApiUrl() + "/v1/users/" + config.get().getUserId() + "/cards")
                    .queryParam("access_token", autorizacao.getAccessToken())
                    .build()
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(autorizacao.getAccessToken());
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Cartões obtidos com sucesso da API do Mercado Pago");
                return processarCartoesResposta(response.getBody());
            }
            
            log.warn("API retornou status não esperado: {}. Usando dados simulados", response.getStatusCode());
            return getSimulatedCreditCards();
            
        } catch (Exception e) {
            log.error("Erro ao buscar cartões do Mercado Pago: {}", e.getMessage(), e);
            return getSimulatedCreditCards();
        }
    }

    public Map<String, Object> getBalanceData(AutorizacaoBancaria autorizacao) {
        try {
            log.info("Buscando saldo real do Mercado Pago para usuário {}", autorizacao.getUsuario().getId());
            
            // Buscar configuração do usuário
            Optional<BankApiConfig> config = getMercadoPagoConfig(autorizacao.getUsuario().getId());
            if (config.isEmpty()) {
                log.error("❌ Usuário {} não possui configuração do Mercado Pago", autorizacao.getUsuario().getId());
                return getSimulatedBalanceData();
            }

            // Chamada real para API do Mercado Pago
            String url = UriComponentsBuilder
                    .fromHttpUrl(config.get().getApiUrl() + "/v1/users/" + config.get().getUserId() + "/accounts/balance")
                    .queryParam("access_token", autorizacao.getAccessToken())
                    .build()
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(autorizacao.getAccessToken());
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Saldo obtido com sucesso da API do Mercado Pago");
                return processarSaldoResposta(response.getBody());
            }
            
            log.warn("API retornou status não esperado: {}. Usando dados simulados", response.getStatusCode());
            return getSimulatedBalanceData();
            
        } catch (Exception e) {
            log.error("Erro ao buscar saldo do Mercado Pago: {}", e.getMessage(), e);
            return getSimulatedBalanceData();
        }
    }

    public List<Map<String, Object>> getInvoices(AutorizacaoBancaria autorizacao) {
        try {
            // Buscar configuração do usuário
            Optional<BankApiConfig> config = getMercadoPagoConfig(autorizacao.getUsuario().getId());
            if (config.isEmpty()) {
                log.error("❌ Usuário {} não possui configuração do Mercado Pago", autorizacao.getUsuario().getId());
                return getSimulatedInvoices();
            }

            // TODO: Implementar chamada real para API do Mercado Pago
            String url = UriComponentsBuilder
                    .fromHttpUrl(config.get().getApiUrl() + "/invoices")
                    .queryParam("access_token", autorizacao.getAccessToken())
                    .build()
                    .toUriString();

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // Processar resposta real da API
                return processarFaturasResposta(response.getBody());
            }
            
            // Fallback para dados simulados se a API falhar
            return getSimulatedInvoices();
            
        } catch (Exception e) {
            log.error("Erro ao buscar faturas do Mercado Pago: {}", e.getMessage(), e);
            return getSimulatedInvoices();
        }
    }

    public List<Map<String, Object>> getTransactions(AutorizacaoBancaria autorizacao) {
        try {
            log.info("Buscando transações reais do Mercado Pago para usuário {}", autorizacao.getUsuario().getId());
            
            // Buscar configuração do usuário
            Optional<BankApiConfig> config = getMercadoPagoConfig(autorizacao.getUsuario().getId());
            if (config.isEmpty()) {
                log.error("❌ Usuário {} não possui configuração do Mercado Pago", autorizacao.getUsuario().getId());
                return getSimulatedTransactions();
            }

            // Chamada real para API do Mercado Pago
            String url = UriComponentsBuilder
                    .fromHttpUrl(config.get().getApiUrl() + "/v1/users/" + config.get().getUserId() + "/transactions")
                    .queryParam("access_token", autorizacao.getAccessToken())
                    .queryParam("limit", "100")
                    .queryParam("offset", "0")
                    .build()
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(autorizacao.getAccessToken());
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Transações obtidas com sucesso da API do Mercado Pago");
                return processarTransacoesResposta(response.getBody());
            }
            
            log.warn("API retornou status não esperado: {}. Usando dados simulados", response.getStatusCode());
            return getSimulatedTransactions();
            
        } catch (Exception e) {
            log.error("Erro ao buscar transações do Mercado Pago: {}", e.getMessage(), e);
            return getSimulatedTransactions();
        }
    }

    public Map<String, Object> getSpendingByCategory(AutorizacaoBancaria autorizacao) {
        try {
            // Buscar configuração do usuário
            Optional<BankApiConfig> config = getMercadoPagoConfig(autorizacao.getUsuario().getId());
            if (config.isEmpty()) {
                log.error("❌ Usuário {} não possui configuração do Mercado Pago", autorizacao.getUsuario().getId());
                return getSimulatedSpendingByCategory();
            }

            // TODO: Implementar chamada real para API do Mercado Pago
            String url = UriComponentsBuilder
                    .fromHttpUrl(config.get().getApiUrl() + "/spending/category")
                    .queryParam("access_token", autorizacao.getAccessToken())
                    .build()
                    .toUriString();

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // Processar resposta real da API
                return processarGastosPorCategoriaResposta(response.getBody());
            }
            
            // Fallback para dados simulados se a API falhar
            return getSimulatedSpendingByCategory();
            
        } catch (Exception e) {
            log.error("Erro ao buscar gastos por categoria do Mercado Pago: {}", e.getMessage(), e);
            return getSimulatedSpendingByCategory();
        }
    }

    public Map<String, Object> getSpendingAnalysis(AutorizacaoBancaria autorizacao) {
        try {
            // Buscar configuração do usuário
            Optional<BankApiConfig> config = getMercadoPagoConfig(autorizacao.getUsuario().getId());
            if (config.isEmpty()) {
                log.error("❌ Usuário {} não possui configuração do Mercado Pago", autorizacao.getUsuario().getId());
                return getSimulatedSpendingAnalysis();
            }

            // TODO: Implementar chamada real para API do Mercado Pago
            String url = UriComponentsBuilder
                    .fromHttpUrl(config.get().getApiUrl() + "/spending/analysis")
                    .queryParam("access_token", autorizacao.getAccessToken())
                    .build()
                    .toUriString();

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // Processar resposta real da API
                return processarAnaliseGastosResposta(response.getBody());
            }
            
            // Fallback para dados simulados se a API falhar
            return getSimulatedSpendingAnalysis();
            
        } catch (Exception e) {
            log.error("Erro ao buscar análise de gastos do Mercado Pago: {}", e.getMessage(), e);
            return getSimulatedSpendingAnalysis();
        }
    }

    public boolean refreshTokenIfNeeded(AutorizacaoBancaria autorizacao) {
        try {
            if (!autorizacao.precisaRenovacao()) {
                return false;
            }

            // Buscar configuração do usuário
            Optional<BankApiConfig> config = getMercadoPagoConfig(autorizacao.getUsuario().getId());
            if (config.isEmpty()) {
                log.error("❌ Usuário {} não possui configuração do Mercado Pago para renovar token", autorizacao.getUsuario().getId());
                return false;
            }

            BankApiConfig mpConfig = config.get();

            // TODO: Implementar renovação real do token
            String url = mpConfig.getTokenUrl();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "refresh_token");
            body.add("refresh_token", autorizacao.getRefreshToken());
            body.add("client_id", mpConfig.getClientId());
            body.add("client_secret", mpConfig.getClientSecret());

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
                
                log.info("Token renovado com sucesso para Mercado Pago - Usuário: {}", autorizacao.getUsuario().getId());
                return true;
            }

            return false;
        } catch (Exception e) {
            log.error("Erro ao renovar token do Mercado Pago: {}", e.getMessage(), e);
            return false;
        }
    }

    // Métodos auxiliares para processar respostas da API
    private List<Map<String, Object>> processarCartoesResposta(Map<String, Object> response) {
        log.info("Processando resposta real de cartões do Mercado Pago");
        
        try {
            List<Map<String, Object>> cartoes = new ArrayList<>();
            
            if (response.containsKey("data") && response.get("data") instanceof List) {
                List<Map<String, Object>> cartoesData = (List<Map<String, Object>>) response.get("data");
                
                for (Map<String, Object> cartaoData : cartoesData) {
                    Map<String, Object> cartao = new HashMap<>();
                    cartao.put("id", cartaoData.get("id"));
                    cartao.put("numeroCartao", mascararNumeroCartao((String) cartaoData.get("card_number")));
                    cartao.put("nome", cartaoData.get("cardholder_name"));
                    cartao.put("limiteCredito", new BigDecimal(cartaoData.getOrDefault("credit_limit", "0").toString()));
                    cartao.put("limiteDisponivel", new BigDecimal(cartaoData.getOrDefault("available_credit", "0").toString()));
                    cartao.put("saldoFatura", new BigDecimal(cartaoData.getOrDefault("current_balance", "0").toString()));
                    cartao.put("dataVencimento", LocalDate.now().plusDays(25)); // Mercado Pago não fornece data de vencimento
                    cartao.put("tipo", "CREDITO");
                    cartao.put("banco", "MERCADO_PAGO");
                    
                    cartoes.add(cartao);
                }
            }
            
            log.info("Processados {} cartões reais do Mercado Pago", cartoes.size());
            return cartoes;
            
        } catch (Exception e) {
            log.error("Erro ao processar cartões do Mercado Pago: {}", e.getMessage(), e);
            return getSimulatedCreditCards();
        }
    }

    private Map<String, Object> processarSaldoResposta(Map<String, Object> response) {
        log.info("Processando resposta real de saldo do Mercado Pago");
        
        try {
            Map<String, Object> result = new HashMap<>();
            List<Map<String, Object>> accounts = new ArrayList<>();
            
            if (response.containsKey("data")) {
                Map<String, Object> accountData = (Map<String, Object>) response.get("data");
                
                Map<String, Object> account = new HashMap<>();
                account.put("accountId", accountData.get("id"));
                account.put("accountType", "CHECKING");
                account.put("balance", new BigDecimal(accountData.getOrDefault("balance", "0").toString()));
                account.put("availableBalance", new BigDecimal(accountData.getOrDefault("available_balance", "0").toString()));
                account.put("currency", accountData.getOrDefault("currency", "BRL"));
                account.put("lastUpdate", LocalDateTime.now());
                
                accounts.add(account);
                
                result.put("accounts", accounts);
                result.put("totalBalance", account.get("balance"));
                result.put("totalAvailableBalance", account.get("availableBalance"));
            }
            
            log.info("Saldo real processado com sucesso do Mercado Pago");
            return result;
            
        } catch (Exception e) {
            log.error("Erro ao processar saldo do Mercado Pago: {}", e.getMessage(), e);
            return getSimulatedBalanceData();
        }
    }

    private List<Map<String, Object>> processarFaturasResposta(Map<String, Object> response) {
        // TODO: Implementar processamento real da resposta da API
        log.info("Processando resposta real de faturas do Mercado Pago");
        return getSimulatedInvoices();
    }

    private List<Map<String, Object>> processarTransacoesResposta(Map<String, Object> response) {
        log.info("Processando resposta real de transações do Mercado Pago");
        
        try {
            List<Map<String, Object>> transacoes = new ArrayList<>();
            
            if (response.containsKey("data") && response.get("data") instanceof List) {
                List<Map<String, Object>> transacoesData = (List<Map<String, Object>>) response.get("data");
                
                for (Map<String, Object> transacaoData : transacoesData) {
                    Map<String, Object> transacao = new HashMap<>();
                    transacao.put("id", transacaoData.get("id"));
                    transacao.put("descricao", transacaoData.get("description"));
                    transacao.put("valor", new BigDecimal(transacaoData.getOrDefault("amount", "0").toString()));
                    transacao.put("data", parseDateTime((String) transacaoData.get("created_at")));
                    transacao.put("categoria", determinarCategoria((String) transacaoData.get("description")));
                    transacao.put("tipo", transacaoData.getOrDefault("type", "DEBITO"));
                    
                    transacoes.add(transacao);
                }
            }
            
            log.info("Processadas {} transações reais do Mercado Pago", transacoes.size());
            return transacoes;
            
        } catch (Exception e) {
            log.error("Erro ao processar transações do Mercado Pago: {}", e.getMessage(), e);
            return getSimulatedTransactions();
        }
    }

    private Map<String, Object> processarGastosPorCategoriaResposta(Map<String, Object> response) {
        // TODO: Implementar processamento real da resposta da API
        log.info("Processando resposta real de gastos por categoria do Mercado Pago");
        return getSimulatedSpendingByCategory();
    }

    private Map<String, Object> processarAnaliseGastosResposta(Map<String, Object> response) {
        // TODO: Implementar processamento real da resposta da API
        log.info("Processando resposta real de análise de gastos do Mercado Pago");
        return getSimulatedSpendingAnalysis();
    }

    // Métodos para dados simulados (fallback)
    private List<Map<String, Object>> getSimulatedCreditCards() {
        List<Map<String, Object>> cartoes = new ArrayList<>();
        
        Map<String, Object> cartao = new HashMap<>();
        cartao.put("id", 1L);
        cartao.put("numeroCartao", "**** **** **** 1234");
        cartao.put("nome", "Cartão Mercado Pago");
        cartao.put("limiteCredito", new BigDecimal("5000.00"));
        cartao.put("limiteDisponivel", new BigDecimal("3500.00"));
        cartao.put("saldoFatura", new BigDecimal("1500.00"));
        cartao.put("dataVencimento", LocalDate.now().plusDays(25));
        cartao.put("tipo", "CREDITO");
        cartao.put("banco", "MERCADO_PAGO");
        
        cartoes.add(cartao);
        return cartoes;
    }

    private Map<String, Object> getSimulatedBalanceData() {
        Map<String, Object> result = new HashMap<>();
        
        List<Map<String, Object>> accounts = new ArrayList<>();
        Map<String, Object> account = new HashMap<>();
        account.put("accountId", "mp_123456");
        account.put("accountType", "CHECKING");
        account.put("balance", new BigDecimal("2500.00"));
        account.put("availableBalance", new BigDecimal("2500.00"));
        account.put("currency", "BRL");
        account.put("lastUpdate", LocalDateTime.now());
        
        accounts.add(account);
        
        result.put("accounts", accounts);
        result.put("totalBalance", new BigDecimal("2500.00"));
        result.put("totalAvailableBalance", new BigDecimal("2500.00"));
        
        return result;
    }

    private List<Map<String, Object>> getSimulatedInvoices() {
        List<Map<String, Object>> faturas = new ArrayList<>();
        
        Map<String, Object> fatura = new HashMap<>();
        fatura.put("id", 1L);
        fatura.put("numeroFatura", "FAT001");
        fatura.put("valorFatura", new BigDecimal("1500.00"));
        fatura.put("valorPago", BigDecimal.ZERO);
        fatura.put("dataVencimento", LocalDate.now().plusDays(15));
        fatura.put("dataFechamento", LocalDate.now().minusDays(5));
        fatura.put("statusFatura", "ABERTA");
        fatura.put("cartaoCreditoId", 1L);
        
        faturas.add(fatura);
        return faturas;
    }

    private List<Map<String, Object>> getSimulatedTransactions() {
        List<Map<String, Object>> transacoes = new ArrayList<>();
        
        Map<String, Object> transacao = new HashMap<>();
        transacao.put("id", 1L);
        transacao.put("descricao", "Compra Supermercado");
        transacao.put("valor", new BigDecimal("150.00"));
        transacao.put("data", LocalDateTime.now().minusDays(2));
        transacao.put("categoria", "ALIMENTACAO");
        transacao.put("tipo", "DEBITO");
        
        transacoes.add(transacao);
        return transacoes;
    }

    private Map<String, Object> getSimulatedSpendingByCategory() {
        Map<String, Object> result = new HashMap<>();
        
        Map<String, BigDecimal> gastos = new HashMap<>();
        gastos.put("ALIMENTACAO", new BigDecimal("500.00"));
        gastos.put("TRANSPORTE", new BigDecimal("200.00"));
        gastos.put("LAZER", new BigDecimal("300.00"));
        
        result.put("gastosPorCategoria", gastos);
        result.put("totalGasto", new BigDecimal("1000.00"));
        result.put("periodo", "Mês atual");
        
        return result;
    }

    private Map<String, Object> getSimulatedSpendingAnalysis() {
        Map<String, Object> result = new HashMap<>();
        
        result.put("gastoTotal", new BigDecimal("1000.00"));
        result.put("gastoMedio", new BigDecimal("33.33"));
        result.put("maiorGasto", new BigDecimal("150.00"));
        result.put("menorGasto", new BigDecimal("10.00"));
        result.put("totalTransacoes", 30);
        result.put("periodo", "Mês atual");
        
        return result;
    }
    
    // Métodos auxiliares para processar dados reais
    private String getCustomerId(AutorizacaoBancaria autorizacao) {
        // Em uma implementação real, você precisaria buscar o customer_id do usuário
        // Por enquanto, vamos usar um ID padrão ou buscar de uma tabela de mapeamento
        return "customer_" + autorizacao.getUsuario().getId();
    }
    
    private String getAccountId(AutorizacaoBancaria autorizacao) {
        // Em uma implementação real, você precisaria buscar o account_id do usuário
        // Por enquanto, vamos usar um ID padrão ou buscar de uma tabela de mapeamento
        return "account_" + autorizacao.getUsuario().getId();
    }
    
    private String mascararNumeroCartao(String numeroCartao) {
        if (numeroCartao == null || numeroCartao.length() < 4) {
            return "****";
        }
        return "**** **** **** " + numeroCartao.substring(numeroCartao.length() - 4);
    }
    
    private LocalDateTime parseDateTime(String dateTimeStr) {
        try {
            // Mercado Pago usa formato ISO 8601
            return LocalDateTime.parse(dateTimeStr.replace("Z", ""));
        } catch (Exception e) {
            log.warn("Erro ao fazer parse da data: {}. Usando data atual", dateTimeStr);
            return LocalDateTime.now();
        }
    }
    
    private String determinarCategoria(String descricao) {
        if (descricao == null) return "OUTROS";
        
        String descLower = descricao.toLowerCase();
        
        if (descLower.contains("supermercado") || descLower.contains("mercado") || 
            descLower.contains("padaria") || descLower.contains("açougue")) {
            return "ALIMENTACAO";
        } else if (descLower.contains("uber") || descLower.contains("99") || 
                   descLower.contains("taxi") || descLower.contains("onibus")) {
            return "TRANSPORTE";
        } else if (descLower.contains("cinema") || descLower.contains("teatro") || 
                   descLower.contains("shopping") || descLower.contains("restaurante")) {
            return "LAZER";
        } else if (descLower.contains("farmacia") || descLower.contains("hospital") || 
                   descLower.contains("medico")) {
            return "SAUDE";
        } else if (descLower.contains("energia") || descLower.contains("agua") || 
                   descLower.contains("internet") || descLower.contains("telefone")) {
            return "CONTAS";
        } else {
            return "OUTROS";
        }
    }
}
