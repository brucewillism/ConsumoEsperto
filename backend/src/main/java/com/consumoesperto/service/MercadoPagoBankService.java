package com.consumoesperto.service;

import com.consumoesperto.dto.*;
import com.consumoesperto.model.AutorizacaoBancaria;
import com.consumoesperto.model.BankApiConfig;
import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.Fatura;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.BankApiConfigRepository;
import com.consumoesperto.repository.AutorizacaoBancariaRepository;
import com.consumoesperto.repository.UsuarioRepository;
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
import java.util.Arrays;

@Service
@Slf4j
public class MercadoPagoBankService {

    private final RestTemplate restTemplate;
    private final BankApiConfigRepository bankApiConfigRepository;
    private final AutorizacaoBancariaRepository autorizacaoBancariaRepository;
    private final UsuarioRepository usuarioRepository;

    public MercadoPagoBankService(RestTemplate restTemplate, BankApiConfigRepository bankApiConfigRepository, 
                                 AutorizacaoBancariaRepository autorizacaoBancariaRepository, 
                                 UsuarioRepository usuarioRepository) {
        this.restTemplate = restTemplate;
        this.bankApiConfigRepository = bankApiConfigRepository;
        this.autorizacaoBancariaRepository = autorizacaoBancariaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Busca configuração do Mercado Pago para um usuário
     */
    private Optional<BankApiConfig> getMercadoPagoConfig(Long userId) {
<<<<<<< HEAD
        try {
            log.debug("🔍 Buscando configuração do Mercado Pago para usuário: {}", userId);
            Optional<BankApiConfig> config = bankApiConfigRepository.findByUsuarioIdAndBanco(userId, "MERCADOPAGO");
            
            if (config.isPresent() && config.get().getAtivo()) {
                log.debug("✅ Configuração encontrada para usuário: {}", userId);
                return config;
=======
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
                
                // Salvar autorização bancária na tabela
                salvarAutorizacaoBancaria(userId, tokenData);
                
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
>>>>>>> origin/main
            }
            
            log.warn("⚠️ Usuário {} não possui configuração do Mercado Pago", userId);
            return Optional.empty();
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar configuração do Mercado Pago para usuário {}: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Busca saldo real do Mercado Pago
     */
    public Map<String, Object> getRealBalance(AutorizacaoBancaria autorizacao) {
        try {
            log.info("Buscando saldo real do Mercado Pago para usuário {}", autorizacao.getUsuario().getId());
            
<<<<<<< HEAD
            // Tentar diferentes endpoints para saldo
            List<String> endpoints = Arrays.asList(
                "https://api.mercadopago.com/v1/account/balance",
                "https://api.mercadopago.com/v1/users/me",
                "https://api.mercadopago.com/v1/account/settings"
            );
            
=======
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

>>>>>>> origin/main
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + autorizacao.getAccessToken());
            headers.set("Content-Type", "application/json");
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            for (String endpoint : endpoints) {
                try {
                    ResponseEntity<Map> response = restTemplate.exchange(
                            endpoint, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                        log.info("✅ Saldo obtido com sucesso usando endpoint: {}", endpoint);
                return processarSaldoResposta(response.getBody());
                    }
                } catch (Exception e) {
                    log.debug("⚠️ Endpoint {} falhou: {}", endpoint, e.getMessage());
                }
            }
            
            log.warn("⚠️ Todos os endpoints de saldo falharam");
            return new HashMap<>();
            
        } catch (Exception e) {
            log.error("Erro ao buscar saldo do Mercado Pago: {}", e.getMessage(), e);
            return new HashMap<>();
        }
    }

    /**
     * Busca customer_id do usuário no Mercado Pago
     */
    private String buscarCustomerId(String accessToken, String email) {
        try {
<<<<<<< HEAD
            log.info("🔍 Buscando customer_id para email: {}", email);
            
            String searchUrl = "https://api.mercadopago.com/v1/customers/search?email=" + email;
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            headers.set("Content-Type", "application/json");
            
            HttpEntity<String> request = new HttpEntity<>(headers);
            log.debug("📤 Fazendo requisição para: {}", searchUrl);
            ResponseEntity<Map> response = restTemplate.exchange(
                    searchUrl, HttpMethod.GET, request, Map.class);
            
            log.info("📥 Resposta recebida - Status: {}", response.getStatusCode());
=======
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
>>>>>>> origin/main
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                log.debug("📦 Chaves na resposta: {}", responseBody.keySet());
                
                if (responseBody.containsKey("results") && responseBody.get("results") instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> results = (List<Map<String, Object>>) responseBody.get("results");
                    
                    log.info("📋 Encontrados {} resultados na busca por customer", results.size());
                    
                    if (!results.isEmpty()) {
                        String customerId = results.get(0).get("id").toString();
                        log.info("✅ Customer ID encontrado: {}", customerId);
                        return customerId;
                    } else {
                        log.warn("⚠️ Lista de resultados vazia para email: {}", email);
                    }
                } else {
                    log.warn("⚠️ Resposta não contém 'results' ou não é uma lista. Estrutura: {}", responseBody.keySet());
                }
            } else {
                log.warn("⚠️ Resposta com status não-2xx ou body nulo. Status: {}", 
                    response.getStatusCode());
            }
            
            log.warn("⚠️ Customer ID não encontrado para email: {}", email);
            return null;
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar customer_id: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Busca cartões reais do Mercado Pago
     */
    public List<Map<String, Object>> getRealCreditCards(AutorizacaoBancaria autorizacao) {
        try {
            log.info("💳 Buscando cartões reais do Mercado Pago para usuário {}", autorizacao.getUsuario().getId());
            
            // Buscar customer_id primeiro
            Optional<Usuario> usuarioOpt = usuarioRepository.findById(autorizacao.getUsuario().getId());
            if (usuarioOpt.isEmpty()) {
                log.error("❌ Usuário {} não encontrado", autorizacao.getUsuario().getId());
                return new ArrayList<>();
            }

            Usuario usuario = usuarioOpt.get();
            log.info("👤 Usuário encontrado: {} - Email: {}", usuario.getId(), usuario.getEmail());
            
            String customerId = buscarCustomerId(autorizacao.getAccessToken(), usuario.getEmail());
            if (customerId == null) {
                log.warn("⚠️ Customer ID não encontrado para usuário {}, retornando lista vazia", usuario.getId());
                return new ArrayList<>();
            }
            
            log.info("✅ Customer ID obtido com sucesso: {}", customerId);

            // Usar endpoint correto da API do Mercado Pago
            String url = "https://api.mercadopago.com/v1/customers/" + customerId + "/cards";
            log.info("🌐 Fazendo requisição para: {}", url);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + autorizacao.getAccessToken());
            headers.set("Content-Type", "application/json");
            
            HttpEntity<String> request = new HttpEntity<>(headers);
            ResponseEntity<Object> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, Object.class);
            
            log.info("📥 Resposta recebida - Status: {}", response.getStatusCode());
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("✅ Cartões obtidos com sucesso da API do Mercado Pago");
                
                // Processar resposta (pode ser List ou Map)
                Object body = response.getBody();
                log.debug("📦 Tipo do body da resposta: {}", body.getClass().getName());
                
                List<Map<String, Object>> cartoes = new ArrayList<>();
                
                if (body instanceof List) {
                    // API retorna lista diretamente
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> cardsList = (List<Map<String, Object>>) body;
                    log.info("📋 API retornou {} cartões diretamente como lista", cardsList.size());
                    
                    // Processar lista diretamente
                    cartoes = processarListaCartoes(cardsList);
                } else if (body instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> bodyMap = (Map<String, Object>) body;
                    log.debug("📋 API retornou objeto Map com chaves: {}", bodyMap.keySet());
                    cartoes = processarCartoesResposta(bodyMap);
                } else {
                    log.warn("⚠️ Resposta em formato desconhecido: {}", body.getClass().getName());
                }
                
                // Se não encontrou cartões na API, criar cartão virtual baseado nos pagamentos
                if (cartoes.isEmpty()) {
                    log.info("📋 Nenhum cartão encontrado na API. Criando cartão virtual baseado nos pagamentos...");
                    List<Map<String, Object>> cartaoVirtual = criarCartaoVirtualDosPagamentos(autorizacao);
                    if (!cartaoVirtual.isEmpty()) {
                        log.info("✅ Cartão virtual criado: {}", cartaoVirtual.size());
                        return cartaoVirtual;
                    }
                }
                
                return cartoes;
            }
            
            log.warn("⚠️ API retornou status não esperado: {}", response.getStatusCode());
            return new ArrayList<>();
            
        } catch (Exception e) {
            log.error("Erro ao buscar cartões do Mercado Pago: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Busca faturas reais do Mercado Pago
     */
    public List<Map<String, Object>> getRealInvoices(AutorizacaoBancaria autorizacao) {
        try {
            log.info("Buscando faturas reais do Mercado Pago para usuário {}", autorizacao.getUsuario().getId());
            
            // Tentar diferentes endpoints para faturas (payments)
            List<String> endpoints = Arrays.asList(
                "https://api.mercadopago.com/v1/payments/search?limit=100&offset=0&sort=date_created&criteria=desc",
                "https://api.mercadopago.com/v1/payments",
                "https://api.mercadopago.com/v1/account/movements"
            );
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + autorizacao.getAccessToken());
            headers.set("Content-Type", "application/json");
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            for (String endpoint : endpoints) {
                try {
                    ResponseEntity<Map> response = restTemplate.exchange(
                            endpoint, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                        log.info("✅ Faturas obtidas com sucesso usando endpoint: {}", endpoint);
                return processarFaturasResposta(response.getBody());
                    }
                } catch (Exception e) {
                    log.debug("⚠️ Endpoint {} falhou: {}", endpoint, e.getMessage());
                }
            }
            
            log.warn("⚠️ Todos os endpoints de faturas falharam");
            return new ArrayList<>();
            
        } catch (Exception e) {
            log.error("Erro ao buscar faturas do Mercado Pago: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Busca transações reais do Mercado Pago
     */
    public List<Map<String, Object>> getTransactions(AutorizacaoBancaria autorizacao) {
        try {
            log.info("Buscando transações reais do Mercado Pago para usuário {}", autorizacao.getUsuario().getId());
            
            // Buscar configuração do usuário
            Optional<BankApiConfig> config = getMercadoPagoConfig(autorizacao.getUsuario().getId());
            if (config.isEmpty()) {
                log.error("❌ Usuário {} não possui configuração do Mercado Pago", autorizacao.getUsuario().getId());
<<<<<<< HEAD
                return new ArrayList<>();
=======
                return getSimulatedTransactions();
>>>>>>> origin/main
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
            
            log.warn("API retornou status não esperado: {}", response.getStatusCode());
            return new ArrayList<>();
            
        } catch (Exception e) {
            log.error("Erro ao buscar transações do Mercado Pago: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Busca gastos por categoria reais do Mercado Pago
     */
    public Map<String, Object> getSpendingByCategory(AutorizacaoBancaria autorizacao) {
        try {
<<<<<<< HEAD
            log.info("Buscando gastos por categoria reais do Mercado Pago para usuário {}", autorizacao.getUsuario().getId());
            
=======
>>>>>>> origin/main
            // Buscar configuração do usuário
            Optional<BankApiConfig> config = getMercadoPagoConfig(autorizacao.getUsuario().getId());
            if (config.isEmpty()) {
                log.error("❌ Usuário {} não possui configuração do Mercado Pago", autorizacao.getUsuario().getId());
<<<<<<< HEAD
                return new HashMap<>();
            }

            // Chamada real para API do Mercado Pago
            String url = UriComponentsBuilder
                    .fromHttpUrl(config.get().getApiUrl() + "/v1/users/" + config.get().getUserId() + "/spending/category")
=======
                return getSimulatedSpendingByCategory();
            }

            // TODO: Implementar chamada real para API do Mercado Pago
            String url = UriComponentsBuilder
                    .fromHttpUrl(config.get().getApiUrl() + "/spending/category")
>>>>>>> origin/main
                    .queryParam("access_token", autorizacao.getAccessToken())
                    .queryParam("period", "month")
                    .build()
                    .toUriString();

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Gastos por categoria obtidos com sucesso da API do Mercado Pago");
                return processarGastosCategoriaResposta(response.getBody());
            }
            
            log.warn("API retornou status não esperado: {}", response.getStatusCode());
            return new HashMap<>();
            
        } catch (Exception e) {
            log.error("Erro ao buscar gastos por categoria do Mercado Pago: {}", e.getMessage(), e);
            return new HashMap<>();
        }
    }

    /**
     * Busca análise de gastos reais do Mercado Pago
     */
    public Map<String, Object> getSpendingAnalysis(AutorizacaoBancaria autorizacao) {
        try {
<<<<<<< HEAD
            log.info("Buscando análise de gastos reais do Mercado Pago para usuário {}", autorizacao.getUsuario().getId());
            
=======
>>>>>>> origin/main
            // Buscar configuração do usuário
            Optional<BankApiConfig> config = getMercadoPagoConfig(autorizacao.getUsuario().getId());
            if (config.isEmpty()) {
                log.error("❌ Usuário {} não possui configuração do Mercado Pago", autorizacao.getUsuario().getId());
<<<<<<< HEAD
                return new HashMap<>();
            }

            // Chamada real para API do Mercado Pago
            String url = UriComponentsBuilder
                    .fromHttpUrl(config.get().getApiUrl() + "/v1/users/" + config.get().getUserId() + "/spending/analysis")
=======
                return getSimulatedSpendingAnalysis();
            }

            // TODO: Implementar chamada real para API do Mercado Pago
            String url = UriComponentsBuilder
                    .fromHttpUrl(config.get().getApiUrl() + "/spending/analysis")
>>>>>>> origin/main
                    .queryParam("access_token", autorizacao.getAccessToken())
                    .queryParam("period", "month")
                    .build()
                    .toUriString();

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Análise de gastos obtida com sucesso da API do Mercado Pago");
                return processarAnaliseGastosResposta(response.getBody());
            }
            
            log.warn("API retornou status não esperado: {}", response.getStatusCode());
            return new HashMap<>();
            
        } catch (Exception e) {
            log.error("Erro ao buscar análise de gastos do Mercado Pago: {}", e.getMessage(), e);
<<<<<<< HEAD
            return new HashMap<>();
=======
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
>>>>>>> origin/main
        }
    }

    // Métodos auxiliares para processar dados reais
    private Map<String, Object> processarSaldoResposta(Map<String, Object> response) {
        log.info("Processando resposta real de saldo do Mercado Pago");
        
        Map<String, Object> resultado = new HashMap<>();
        
        try {
            if (response != null && response.containsKey("available_balance")) {
                Object saldo = response.get("available_balance");
                if (saldo instanceof Number) {
                    resultado.put("saldo", ((Number) saldo).doubleValue());
                } else {
                    resultado.put("saldo", 0.0);
                }
            } else {
                resultado.put("saldo", 0.0);
            }
            
            // Limite padrão do Mercado Pago
            resultado.put("limite", 500.0);
            resultado.put("limiteDisponivel", resultado.get("saldo"));
            
            log.info("Saldo processado: {}", resultado);
            
        } catch (Exception e) {
            log.error("Erro ao processar saldo: {}", e.getMessage());
            resultado.put("saldo", 0.0);
            resultado.put("limite", 0.0);
            resultado.put("limiteDisponivel", 0.0);
        }
        
        return resultado;
    }

    private List<Map<String, Object>> processarCartoesResposta(Map<String, Object> response) {
        log.info("Processando resposta real de cartões do Mercado Pago");
        
        List<Map<String, Object>> cartoes = new ArrayList<>();
        
        try {
            if (response == null) {
                log.warn("⚠️ Resposta nula ao processar cartões");
                return cartoes;
            }
            
            log.debug("📦 Estrutura da resposta: {}", response.keySet());
            
            // Tentar diferentes formatos de resposta
            List<Map<String, Object>> cardsList = null;
            
            // Formato 1: resposta tem "results"
            if (response.containsKey("results")) {
                Object results = response.get("results");
                if (results instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> list = (List<Map<String, Object>>) results;
                    cardsList = list;
                    log.debug("✅ Encontrado formato 'results' com {} cartões", list.size());
                }
            }
            // Formato 2: resposta tem "data"
            else if (response.containsKey("data")) {
                Object data = response.get("data");
                if (data instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> list = (List<Map<String, Object>>) data;
                    cardsList = list;
                    log.debug("✅ Encontrado formato 'data' com {} cartões", list.size());
                }
            }
            // Formato 3: resposta é uma lista direta
            else if (response instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> list = (List<Map<String, Object>>) response;
                cardsList = list;
                log.debug("✅ Encontrado formato de lista direta com {} cartões", list.size());
            }
            
            // Processar cartões encontrados
            if (cardsList != null && !cardsList.isEmpty()) {
                    for (Map<String, Object> card : cardsList) {
                    try {
                        Map<String, Object> cartao = new HashMap<>();
                        
                        // ID do cartão
                        Object idObj = card.get("id");
                        if (idObj != null) {
                            cartao.put("id", idObj.toString());
                        } else {
                            log.warn("⚠️ Cartão sem ID, pulando...");
                            continue;
                        }
                        
                        // Últimos dígitos
                        Object ultimosDigitosObj = card.get("last_four_digits");
                        String ultimosDigitos = "****";
                        if (ultimosDigitosObj != null) {
                            ultimosDigitos = ultimosDigitosObj.toString();
                        }
                        cartao.put("numero", "****" + ultimosDigitos);
                        
                        // Limite do cartão
                        Object limiteObj = card.get("credit_limit");
                        if (limiteObj != null) {
                            if (limiteObj instanceof Number) {
                                cartao.put("limite", ((Number) limiteObj).doubleValue());
                            } else {
                                cartao.put("limite", 500.0); // Padrão
                            }
                        } else {
                            cartao.put("limite", 500.0); // Padrão para cartões virtuais
                        }
                        
                        // Saldo disponível
                        Object saldoObj = card.get("available_amount");
                        if (saldoObj != null) {
                            if (saldoObj instanceof Number) {
                                cartao.put("saldoDisponivel", ((Number) saldoObj).doubleValue());
                            } else {
                                cartao.put("saldoDisponivel", cartao.get("limite"));
                            }
                        } else {
                            cartao.put("saldoDisponivel", cartao.get("limite"));
                        }
                        
                        // Nome do portador
                        Object nomeObj = card.get("cardholder_name");
                        String nome = "Cartão Mercado Pago";
                        if (nomeObj != null && !nomeObj.toString().trim().isEmpty()) {
                            nome = nomeObj.toString();
                        }
                        cartao.put("nome", nome);
                        
                        // Bandeira do cartão
                        Object paymentMethodObj = card.get("payment_method");
                        String bandeira = "VISA";
                        if (paymentMethodObj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> pm = (Map<String, Object>) paymentMethodObj;
                            Object pmIdObj = pm.get("id");
                            if (pmIdObj != null) {
                                bandeira = pmIdObj.toString().toUpperCase();
                            }
                        }
                        cartao.put("bandeira", bandeira);
                        
                        cartoes.add(cartao);
                        log.debug("✅ Cartão processado: {} - {} - Limite: {}", nome, ultimosDigitos, cartao.get("limite"));
                        
                    } catch (Exception e) {
                        log.warn("⚠️ Erro ao processar cartão individual: {}", e.getMessage());
                    }
                }
            } else {
                log.warn("⚠️ Nenhum cartão encontrado na resposta. Chaves disponíveis: {}", response.keySet());
            }
            
            log.info("✅ {} cartões processados com sucesso", cartoes.size());
            
        } catch (Exception e) {
            log.error("❌ Erro ao processar cartões: {}", e.getMessage(), e);
        }
        
        return cartoes;
    }

    /**
     * Processa lista direta de cartões da API
     */
    private List<Map<String, Object>> processarListaCartoes(List<Map<String, Object>> cardsList) {
        log.info("Processando lista direta de {} cartões", cardsList.size());
        
        List<Map<String, Object>> cartoes = new ArrayList<>();
        
        if (cardsList == null || cardsList.isEmpty()) {
            log.warn("⚠️ Lista de cartões vazia ou nula");
            return cartoes;
        }
        
        for (Map<String, Object> card : cardsList) {
            try {
                Map<String, Object> cartao = new HashMap<>();
                
                // ID do cartão
                Object idObj = card.get("id");
                if (idObj != null) {
                    cartao.put("id", idObj.toString());
                } else {
                    log.warn("⚠️ Cartão sem ID, pulando...");
                    continue;
                }
                
                // Últimos dígitos
                Object ultimosDigitosObj = card.get("last_four_digits");
                String ultimosDigitos = "****";
                if (ultimosDigitosObj != null) {
                    ultimosDigitos = ultimosDigitosObj.toString();
                }
                cartao.put("numero", "****" + ultimosDigitos);
                
                // Limite do cartão
                Object limiteObj = card.get("credit_limit");
                if (limiteObj != null) {
                    if (limiteObj instanceof Number) {
                        cartao.put("limite", ((Number) limiteObj).doubleValue());
                    } else {
                        cartao.put("limite", 500.0);
                    }
                } else {
                    cartao.put("limite", 500.0);
                }
                
                // Saldo disponível
                Object saldoObj = card.get("available_amount");
                if (saldoObj != null) {
                    if (saldoObj instanceof Number) {
                        cartao.put("saldoDisponivel", ((Number) saldoObj).doubleValue());
                    } else {
                        cartao.put("saldoDisponivel", cartao.get("limite"));
                    }
                } else {
                    cartao.put("saldoDisponivel", cartao.get("limite"));
                }
                
                // Nome do portador
                Object nomeObj = card.get("cardholder_name");
                String nome = "Cartão Mercado Pago";
                if (nomeObj != null && !nomeObj.toString().trim().isEmpty()) {
                    nome = nomeObj.toString();
                }
                cartao.put("nome", nome);
                
                // Bandeira do cartão
                Object paymentMethodObj = card.get("payment_method");
                String bandeira = "VISA";
                if (paymentMethodObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> pm = (Map<String, Object>) paymentMethodObj;
                    Object pmIdObj = pm.get("id");
                    if (pmIdObj != null) {
                        bandeira = pmIdObj.toString().toUpperCase();
                    }
                }
                cartao.put("bandeira", bandeira);
                
                cartoes.add(cartao);
                log.debug("✅ Cartão processado: {} - {} - Limite: {}", nome, ultimosDigitos, cartao.get("limite"));
                
            } catch (Exception e) {
                log.warn("⚠️ Erro ao processar cartão individual: {}", e.getMessage());
            }
        }
        
        log.info("✅ {} cartões processados com sucesso da lista direta", cartoes.size());
        return cartoes;
    }

    /**
     * Cria um cartão virtual baseado nas informações dos pagamentos quando não há cartão salvo na API
     */
    private List<Map<String, Object>> criarCartaoVirtualDosPagamentos(AutorizacaoBancaria autorizacao) {
        try {
            log.info("💳 Criando cartão virtual baseado nos pagamentos do Mercado Pago");
            
            // Buscar pagamentos para extrair informações de cartão
            String url = "https://api.mercadopago.com/v1/payments/search?limit=50&offset=0&sort=date_created&criteria=desc";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + autorizacao.getAccessToken());
            headers.set("Content-Type", "application/json");
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            try {
                ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
                
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    Map<String, Object> responseBody = response.getBody();
                    
                    if (responseBody.containsKey("results")) {
                        Object results = responseBody.get("results");
                        if (results instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> paymentsList = (List<Map<String, Object>>) results;
                            
                            // Procurar por pagamentos com cartão
                            Map<String, Object> cartaoVirtual = null;
                            
                            log.debug("📋 Analisando {} pagamentos para criar cartão virtual", paymentsList.size());
                            
                            for (Map<String, Object> payment : paymentsList) {
                                // Log do payment_method_id para debug
                                Object pmIdObj = payment.get("payment_method_id");
                                String pmId = pmIdObj != null ? pmIdObj.toString().toLowerCase() : "null";
                                log.debug("🔍 Payment method: {} - Payment ID: {}", pmId, payment.get("id"));
                                
                                // Verificar se é pagamento com cartão
                                boolean isCardPayment = false;
                                if (pmIdObj != null && !pmId.equals("null")) {
                                    String pmIdLower = pmId.toLowerCase();
                                    isCardPayment = pmIdLower.contains("card") || 
                                                   pmIdLower.contains("credit_card") || 
                                                   pmIdLower.contains("debit_card") ||
                                                   pmIdLower.contains("visa") ||
                                                   pmIdLower.contains("mastercard") ||
                                                   pmIdLower.contains("amex") ||
                                                   pmIdLower.contains("elo");
                                }
                                
                                // Também verificar se tem campo "card" diretamente
                                if (!isCardPayment && payment.containsKey("card")) {
                                    Object cardObj = payment.get("card");
                                    if (cardObj != null) {
                                        isCardPayment = true;
                                        log.debug("✅ Pagamento tem campo 'card' diretamente");
                                    }
                                }
                                
                                if (isCardPayment) {
                                    log.debug("✅ Pagamento com cartão encontrado: {}", payment.get("id"));
                                    
                                    // Verificar se tem informação de cartão
                                    if (payment.containsKey("card")) {
                                        log.debug("✅ Campo 'card' encontrado no pagamento");
                                        Object cardObj = payment.get("card");
                                        if (cardObj instanceof Map) {
                                            @SuppressWarnings("unchecked")
                                            Map<String, Object> card = (Map<String, Object>) cardObj;
                                            
                                            cartaoVirtual = new HashMap<>();
                                            
                                            // ID do cartão
                                            Object cardIdObj = card.get("id");
                                            if (cardIdObj != null) {
                                                cartaoVirtual.put("id", cardIdObj.toString());
                                            } else {
                                                cartaoVirtual.put("id", "virtual_" + payment.get("id"));
                                            }
                                            
                                            // Últimos 4 dígitos
                                            Object lastFourDigitsObj = card.get("last_four_digits");
                                            String lastFourDigits = "****";
                                            if (lastFourDigitsObj != null) {
                                                lastFourDigits = lastFourDigitsObj.toString();
                                            }
                                            cartaoVirtual.put("ultimosDigitos", lastFourDigits);
                                            cartaoVirtual.put("numero", "****" + lastFourDigits);
                                            
                                            // Nome do portador
                                            String cardholderName = "Cartão Virtual Mercado Pago";
                                            if (card.containsKey("cardholder")) {
                                                Object cardholderObj = card.get("cardholder");
                                                if (cardholderObj instanceof Map) {
                                                    @SuppressWarnings("unchecked")
                                                    Map<String, Object> cardholder = (Map<String, Object>) cardholderObj;
                                                    if (cardholder.containsKey("name")) {
                                                        cardholderName = cardholder.get("name").toString();
                                                    }
                                                }
                                            }
                                            cartaoVirtual.put("nome", cardholderName);
                                            cartaoVirtual.put("cardholderName", cardholderName);
                                            
                                            // Dia de vencimento - obrigatório (padrão: 10)
                                            cartaoVirtual.put("diaVencimento", 10);
                                            
                                            // Bandeira
                                            String bandeira = "VISA";
                                            if (payment.containsKey("payment_method_id")) {
                                                String pmIdUpper = pmId.toUpperCase();
                                                if (pmIdUpper.contains("MASTERCARD") || pmIdUpper.contains("MASTER")) {
                                                    bandeira = "MASTERCARD";
                                                } else if (pmIdUpper.contains("VISA")) {
                                                    bandeira = "VISA";
                                                } else if (pmIdUpper.contains("AMEX") || pmIdUpper.contains("AMERICAN")) {
                                                    bandeira = "AMEX";
                                                } else if (pmIdUpper.contains("ELO")) {
                                                    bandeira = "ELO";
                                                }
                                            }
                                            cartaoVirtual.put("bandeira", bandeira);
                                            
                                            // Valores padrão (não disponíveis na API para cartões virtuais)
                                            cartaoVirtual.put("limite", 0.0);
                                            cartaoVirtual.put("saldoDisponivel", 0.0);
                                            cartaoVirtual.put("credit_limit", 0.0);
                                            cartaoVirtual.put("available_amount", 0.0);
                                            
                                            cartaoVirtual.put("payment_method", bandeira.toLowerCase());
                                            
                                            log.info("✅ Cartão virtual criado com sucesso: {} - ****{} - {}", 
                                                cardholderName, lastFourDigits, bandeira);
                                            
                                            // Encontrou um cartão, retorna
                                            break;
                                        } else {
                                            log.debug("⚠️ Campo 'card' não é um Map no pagamento {}", payment.get("id"));
                                        }
                                    } else {
                                        log.debug("⚠️ Pagamento {} não tem campo 'card'", payment.get("id"));
                                        // Tentar usar informações do payment mesmo sem card
                                        if (payment.containsKey("payment_method_id")) {
                                            log.debug("📋 Tentando criar cartão com payment_method_id apenas");
                                            cartaoVirtual = new HashMap<>();
                                            cartaoVirtual.put("id", "virtual_" + payment.get("id"));
                                            cartaoVirtual.put("ultimosDigitos", "****");
                                            cartaoVirtual.put("numero", "****");
                                            cartaoVirtual.put("nome", "Cartão Virtual Mercado Pago");
                                            cartaoVirtual.put("cardholderName", "Cartão Virtual Mercado Pago");
                                            
                                            // Dia de vencimento - obrigatório (padrão: 10)
                                            cartaoVirtual.put("diaVencimento", 10);
                                            
                                            String bandeira = "VISA";
                                            if (pmId.contains("master")) {
                                                bandeira = "MASTERCARD";
                                            } else if (pmId.contains("visa")) {
                                                bandeira = "VISA";
                                            } else if (pmId.contains("amex") || pmId.contains("american")) {
                                                bandeira = "AMEX";
                                            } else if (pmId.contains("elo")) {
                                                bandeira = "ELO";
                                            }
                                            cartaoVirtual.put("bandeira", bandeira);
                                            cartaoVirtual.put("limite", 0.0);
                                            cartaoVirtual.put("saldoDisponivel", 0.0);
                                            cartaoVirtual.put("credit_limit", 0.0);
                                            cartaoVirtual.put("available_amount", 0.0);
                                            cartaoVirtual.put("payment_method", bandeira.toLowerCase());
                                            
                                            log.info("✅ Cartão virtual criado apenas com payment_method_id: {}", bandeira);
                                            break;
                                        }
                                    }
                                }
                            }
                            
                            if (cartaoVirtual != null) {
                                log.info("✅ Cartão virtual criado com sucesso a partir dos pagamentos");
                                return Arrays.asList(cartaoVirtual);
                            } else {
                                log.warn("⚠️ Nenhum pagamento com cartão encontrado nos {} pagamentos analisados. " +
                                    "Verificando estrutura dos pagamentos...", paymentsList.size());
                                
                                // Log da estrutura do primeiro pagamento para debug
                                if (!paymentsList.isEmpty()) {
                                    Map<String, Object> firstPayment = paymentsList.get(0);
                                    log.debug("📋 Estrutura do primeiro pagamento: {}", firstPayment.keySet());
                                    log.debug("📋 Payment method ID do primeiro: {}", firstPayment.get("payment_method_id"));
                                    log.debug("📋 Tem campo 'card'? {}", firstPayment.containsKey("card"));
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("⚠️ Erro ao buscar pagamentos para criar cartão virtual: {}", e.getMessage());
            }
            
            log.warn("⚠️ Não foi possível criar cartão virtual dos pagamentos");
            return new ArrayList<>();
            
        } catch (Exception e) {
            log.error("❌ Erro ao criar cartão virtual dos pagamentos: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    private List<Map<String, Object>> processarFaturasResposta(Map<String, Object> response) {
        log.info("Processando resposta real de faturas do Mercado Pago");
        
        List<Map<String, Object>> faturas = new ArrayList<>();
        
        try {
            if (response != null && response.containsKey("results")) {
                Object results = response.get("results");
                if (results instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> invoicesList = (List<Map<String, Object>>) results;
                    
                    for (Map<String, Object> invoice : invoicesList) {
                        try {
                        Map<String, Object> fatura = new HashMap<>();
                            
                            // ID da fatura
                            Object idObj = invoice.get("id");
                            if (idObj != null) {
                                fatura.put("id", idObj.toString());
                            }
                            
                            // Converter valores para BigDecimal de forma segura
                            Object valorObj = invoice.getOrDefault("transaction_amount", 0.0);
                            BigDecimal valor = BigDecimal.ZERO;
                            if (valorObj instanceof Number) {
                                valor = BigDecimal.valueOf(((Number) valorObj).doubleValue());
                            } else if (valorObj instanceof BigDecimal) {
                                valor = (BigDecimal) valorObj;
                            }
                            
                            fatura.put("valorTotal", valor);
                            fatura.put("valorFatura", valor);
                            fatura.put("valorMinimo", valor); // Valor mínimo igual ao total por padrão
                            
                            // Status da fatura - converter para formato esperado
                            Object statusObj = invoice.getOrDefault("status", "pending");
                            String status = statusObj.toString();
                            // Converter status do Mercado Pago para status do sistema
                            String statusConvertido = "ABERTA";
                            if (status != null) {
                                String statusLower = status.toLowerCase();
                                if (statusLower.equals("approved") || statusLower.equals("paid")) {
                                    statusConvertido = "PAGA";
                                } else if (statusLower.equals("rejected") || statusLower.equals("cancelled")) {
                                    statusConvertido = "CANCELADA";
                                } else if (statusLower.equals("pending") || statusLower.equals("in_process")) {
                                    statusConvertido = "ABERTA";
                                }
                            }
                            fatura.put("status", statusConvertido);
                            
                            // Data de vencimento
                            Object dateExpirationObj = invoice.get("date_of_expiration");
                            String dataVencimentoStr = null;
                            if (dateExpirationObj != null) {
                                dataVencimentoStr = dateExpirationObj.toString();
                            } else {
                                // Se não houver data de expiração, usar data de criação + 30 dias
                                Object dateCreatedObj = invoice.get("date_created");
                                if (dateCreatedObj != null) {
                                    try {
                                        String dateCreated = dateCreatedObj.toString();
                                        java.time.LocalDateTime dataCriacao = java.time.LocalDateTime.parse(
                                            dateCreated.replace("Z", "").replace("T", "T"));
                                        dataVencimentoStr = dataCriacao.plusDays(30).toString();
                                    } catch (Exception e) {
                                        dataVencimentoStr = java.time.LocalDate.now().plusDays(30).toString();
                                    }
                                } else {
                                    dataVencimentoStr = java.time.LocalDate.now().plusDays(30).toString();
                                }
                            }
                            fatura.put("dataVencimento", dataVencimentoStr);
                            
                            // Data de fechamento - usar data de criação ou data atual
                            Object dateCreatedObj = invoice.get("date_created");
                            String dataFechamentoStr = null;
                            if (dateCreatedObj != null) {
                                try {
                                    String dateCreated = dateCreatedObj.toString();
                                    java.time.LocalDateTime dataCriacao = java.time.LocalDateTime.parse(
                                        dateCreated.replace("Z", "").replace("T", "T"));
                                    dataFechamentoStr = dataCriacao.toString();
                                } catch (Exception e) {
                                    dataFechamentoStr = java.time.LocalDateTime.now().toString();
                                }
                            } else {
                                dataFechamentoStr = java.time.LocalDateTime.now().toString();
                            }
                            fatura.put("dataFechamento", dataFechamentoStr);
                            
                            // Nome do cartão - extrair das informações do pagamento
                            String nomeCartao = "Mercado Pago";
                            if (invoice.containsKey("card")) {
                                Object cardObj = invoice.get("card");
                                if (cardObj instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> card = (Map<String, Object>) cardObj;
                                    if (card.containsKey("cardholder")) {
                                        Object cardholderObj = card.get("cardholder");
                                        if (cardholderObj instanceof Map) {
                                            @SuppressWarnings("unchecked")
                                            Map<String, Object> cardholder = (Map<String, Object>) cardholderObj;
                                            if (cardholder.containsKey("name")) {
                                                nomeCartao = cardholder.get("name").toString();
                                            }
                                        }
                                    }
                                    // Se tiver último 4 dígitos, adicionar ao nome
                                    Object lastFourDigitsObj = card.get("last_four_digits");
                                    if (lastFourDigitsObj != null) {
                                        String ultimosDigitos = lastFourDigitsObj.toString();
                                        nomeCartao = "Cartão " + nomeCartao + " ****" + ultimosDigitos;
                                    }
                                }
                            }
                            fatura.put("nomeCartao", nomeCartao);
                            
                            // Descrição - tratar null
                            Object descricaoObj = invoice.getOrDefault("description", null);
                            String descricao = "Pagamento Mercado Pago";
                            if (descricaoObj != null) {
                                descricao = descricaoObj.toString();
                            } else {
                                // Tentar extrair de outros campos com verificação de null
                                Object externalRefObj = invoice.get("external_reference");
                                if (externalRefObj != null) {
                                    descricao = externalRefObj.toString();
                                } else {
                                    Object statementDescObj = invoice.get("statement_descriptor");
                                    if (statementDescObj != null) {
                                        descricao = statementDescObj.toString();
                                    }
                                }
                            }
                            fatura.put("descricao", descricao);
                            
                            // Valor pago - se o status for aprovado/pago
                            BigDecimal valorPago = BigDecimal.ZERO;
                            if (statusConvertido.equals("PAGA")) {
                                valorPago = valor;
                            }
                            fatura.put("valorPago", valorPago);
                            fatura.put("paga", statusConvertido.equals("PAGA"));
                            
                        faturas.add(fatura);
                            log.debug("✅ Fatura processada: {} - {} - R$ {}", 
                                fatura.get("id"), nomeCartao, valor);
                            
                        } catch (Exception e) {
                            log.warn("⚠️ Erro ao processar fatura individual: {} - Stack trace: {}", 
                                e.getMessage(), e.getClass().getName());
                            if (log.isDebugEnabled()) {
                                log.debug("Stack trace completo:", e);
                            }
                            // Continua com próxima fatura
                        }
                    }
                }
            }
            
            log.info("✅ {} faturas processadas com sucesso", faturas.size());
            
        } catch (Exception e) {
            log.error("❌ Erro ao processar faturas: {}", e.getMessage(), e);
        }
        
        return faturas;
    }

    private List<Map<String, Object>> processarTransacoesResposta(Map<String, Object> response) {
        log.info("Processando resposta real de transações do Mercado Pago");
        
        List<Map<String, Object>> transacoes = new ArrayList<>();
        
        try {
            if (response != null && response.containsKey("results")) {
                Object results = response.get("results");
                if (results instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> transactionsList = (List<Map<String, Object>>) results;
                    
                    for (Map<String, Object> transaction : transactionsList) {
                        Map<String, Object> transacao = new HashMap<>();
                        transacao.put("id", transaction.get("id"));
                        transacao.put("description", transaction.getOrDefault("description", "Transação Mercado Pago"));
                        transacao.put("amount", transaction.getOrDefault("transaction_amount", 0.0));
                        transacao.put("date", transaction.getOrDefault("date_created", 
                            java.time.LocalDateTime.now().toString()));
                        transacao.put("type", "DESPESA"); // Mercado Pago geralmente são despesas
                        transacao.put("category", "Pagamentos");
                        transacoes.add(transacao);
                    }
                }
            }
            
            log.info("{} transações processadas", transacoes.size());
            
        } catch (Exception e) {
            log.error("Erro ao processar transações: {}", e.getMessage());
        }
        
        return transacoes;
    }

    private Map<String, Object> processarGastosCategoriaResposta(Map<String, Object> response) {
        log.info("Processando resposta real de gastos por categoria do Mercado Pago");
        
        Map<String, Object> resultado = new HashMap<>();
        Map<String, Double> categorias = new HashMap<>();
        
        try {
            if (response != null && response.containsKey("results")) {
                Object results = response.get("results");
                if (results instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> transactionsList = (List<Map<String, Object>>) results;
                    
                    for (Map<String, Object> transaction : transactionsList) {
                        String categoria = (String) transaction.getOrDefault("description", "Outros");
                        Double valor = ((Number) transaction.getOrDefault("transaction_amount", 0.0)).doubleValue();
                        
                        if (valor > 0) {
                            categorias.merge(categoria, valor, Double::sum);
                        }
                    }
                }
            }
            
            resultado.put("categories", categorias);
            log.info("Gastos por categoria processados: {}", categorias.size());
            
        } catch (Exception e) {
            log.error("Erro ao processar gastos por categoria: {}", e.getMessage());
            resultado.put("categories", new HashMap<>());
        }
        
        return resultado;
    }

    private Map<String, Object> processarAnaliseGastosResposta(Map<String, Object> response) {
        log.info("Processando resposta real de análise de gastos do Mercado Pago");
        
        Map<String, Object> resultado = new HashMap<>();
        
        try {
            double gastosTotal = 0.0;
            double receitasTotal = 0.0;
            int totalTransacoes = 0;
            Map<String, Double> gastosPorDia = new HashMap<>();
            
            if (response != null && response.containsKey("results")) {
                Object results = response.get("results");
                if (results instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> transactionsList = (List<Map<String, Object>>) results;
                    
                    for (Map<String, Object> transaction : transactionsList) {
                        Double valor = ((Number) transaction.getOrDefault("transaction_amount", 0.0)).doubleValue();
                        String status = (String) transaction.getOrDefault("status", "approved");
                        
                        if ("approved".equals(status)) {
                            if (valor > 0) {
                                gastosTotal += valor;
                            } else {
                                receitasTotal += Math.abs(valor);
                            }
                            
                            totalTransacoes++;
                            
                            // Agrupa por dia
                            String data = (String) transaction.getOrDefault("date_created", 
                                java.time.LocalDateTime.now().toString());
                            String dia = data.substring(0, 10); // YYYY-MM-DD
                            gastosPorDia.merge(dia, Math.abs(valor), Double::sum);
                        }
                    }
                }
            }
            
            resultado.put("gastos", gastosTotal);
            resultado.put("receitas", receitasTotal);
            resultado.put("totalTransacoes", totalTransacoes);
            resultado.put("gastosPorDia", gastosPorDia);
            
            log.info("Análise de gastos processada: gastos={}, receitas={}, transações={}", 
                gastosTotal, receitasTotal, totalTransacoes);
            
        } catch (Exception e) {
            log.error("Erro ao processar análise de gastos: {}", e.getMessage());
            resultado.put("gastos", 0.0);
            resultado.put("receitas", 0.0);
            resultado.put("totalTransacoes", 0);
            resultado.put("gastosPorDia", new HashMap<>());
        }
        
        return resultado;
    }

    // Métodos necessários para compatibilidade com outros serviços
    public Map<String, Object> getBankDetails(AutorizacaoBancaria autorizacao) {
        return getRealBalance(autorizacao);
    }

    public boolean refreshTokenIfNeeded(AutorizacaoBancaria autorizacao) {
        try {
            log.info("Verificando se token do Mercado Pago precisa ser renovado");
            
            // Verifica se o token está expirado
            if (autorizacao.isTokenExpirado()) {
                log.info("Token expirado detectado, tentando renovar...");
                
                // Verifica se tem refresh token
                if (autorizacao.getRefreshToken() != null && !autorizacao.getRefreshToken().trim().isEmpty()) {
                    return renovarTokenComRefreshToken(autorizacao);
                } else {
                    log.warn("Refresh token não disponível para renovação automática");
                    return false;
                }
            }
            
            log.info("Token ainda válido, não precisa renovar");
            return true;
            
        } catch (Exception e) {
            log.error("Erro ao verificar/renovar token: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Renova token usando refresh token
     */
    private boolean renovarTokenComRefreshToken(AutorizacaoBancaria autorizacao) {
        try {
            log.info("Renovando token do Mercado Pago usando refresh token");
            
            String url = "https://api.mercadopago.com/oauth/token";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/x-www-form-urlencoded");
            
            // Busca configuração do Mercado Pago
            Optional<BankApiConfig> configOpt = bankApiConfigRepository
                .findByUsuarioIdAndTipoBanco(autorizacao.getUsuario().getId(), "MERCADO_PAGO");
            
            if (configOpt.isEmpty()) {
                log.error("Configuração do Mercado Pago não encontrada");
                return false;
            }
            
            BankApiConfig config = configOpt.get();
            String credentials = Base64.getEncoder().encodeToString(
                (config.getClientId() + ":" + config.getClientSecret()).getBytes());
            headers.set("Authorization", "Basic " + credentials);
            
            String body = "grant_type=refresh_token&refresh_token=" + autorizacao.getRefreshToken();
            
            HttpEntity<String> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                String novoAccessToken = (String) responseBody.get("access_token");
                String novoRefreshToken = (String) responseBody.get("refresh_token");
                Integer expiresIn = (Integer) responseBody.get("expires_in");
                
                if (novoAccessToken != null) {
                    // Atualiza a autorização com o novo token
                    autorizacao.setAccessToken(novoAccessToken);
                    if (novoRefreshToken != null) {
                        autorizacao.setRefreshToken(novoRefreshToken);
                    }
                    if (expiresIn != null) {
                        autorizacao.setDataExpiracao(LocalDateTime.now().plusSeconds(expiresIn));
                    }
                    autorizacao.setDataAtualizacao(LocalDateTime.now());
                    
                    autorizacaoBancariaRepository.save(autorizacao);
                    
                    log.info("Token renovado com sucesso");
                    return true;
                }
            }
            
            log.warn("Falha ao renovar token via refresh token");
            return false;
            
        } catch (Exception e) {
            log.error("Erro ao renovar token via refresh token: {}", e.getMessage());
            return false;
        }
    }

<<<<<<< HEAD
    public String generateAuthUrl(String clientId, String redirectUri, Long userId) {
        // TODO: Implementar geração de URL de autorização
        return "";
    }

    public Map<String, Object> processOAuthCallback(String code, String state, String redirectUri, Long userId) {
        // TODO: Implementar processamento de callback OAuth
        return new HashMap<>();
    }

    public List<Map<String, Object>> getInvoices(AutorizacaoBancaria autorizacao) {
        return getRealInvoices(autorizacao);
    }

    public Map<String, Object> getBalanceData(AutorizacaoBancaria autorizacao) {
        return getRealBalance(autorizacao);
    }

    public boolean testConnection(AutorizacaoBancaria autorizacao) {
        try {
            Map<String, Object> balance = getRealBalance(autorizacao);
            return balance != null && !balance.isEmpty();
        } catch (Exception e) {
            log.error("Erro ao testar conexão: {}", e.getMessage());
            return false;
        }
    }

    public List<Map<String, Object>> getCreditCards(AutorizacaoBancaria autorizacao) {
        return getRealCreditCards(autorizacao);
    }
=======
    /**
     * Salva autorização bancária na tabela autorizacoes_bancarias
     */
    private void salvarAutorizacaoBancaria(Long userId, Map<String, Object> tokenData) {
        try {
            log.info("💾 Salvando autorização bancária para usuário: {}", userId);
            
            // Buscar usuário
            Optional<Usuario> usuarioOpt = usuarioRepository.findById(userId);
            if (usuarioOpt.isEmpty()) {
                log.error("❌ Usuário não encontrado: {}", userId);
                return;
            }
            
            // Extrair dados do token
            String accessToken = (String) tokenData.get("access_token");
            String tokenType = (String) tokenData.get("token_type");
            String scope = (String) tokenData.get("scope");
            Integer expiresIn = (Integer) tokenData.get("expires_in");
            
            if (accessToken == null || accessToken.trim().isEmpty()) {
                log.error("❌ Access token não encontrado nos dados de resposta");
                return;
            }
            
            // Calcular data de expiração
            LocalDateTime dataExpiracao = LocalDateTime.now();
            if (expiresIn != null) {
                dataExpiracao = dataExpiracao.plusSeconds(expiresIn);
            } else {
                // Default: 6 horas
                dataExpiracao = dataExpiracao.plusHours(6);
            }
            
            // Buscar autorização existente
            Optional<AutorizacaoBancaria> authExistente = autorizacaoBancariaRepository
                .findByUsuarioIdAndTipoBanco(userId, "MERCADO_PAGO");
            
            AutorizacaoBancaria autorizacao;
            if (authExistente.isPresent()) {
                // Atualizar autorização existente
                autorizacao = authExistente.get();
                autorizacao.setAccessToken(accessToken);
                autorizacao.setTokenType(tokenType != null ? tokenType : "Bearer");
                autorizacao.setScope(scope);
                autorizacao.setDataAtualizacao(LocalDateTime.now());
                autorizacao.setDataExpiracao(dataExpiracao);
                autorizacao.setAtivo(true);
                log.info("🔄 Atualizando autorização existente para usuário: {}", userId);
            } else {
                // Criar nova autorização
                autorizacao = new AutorizacaoBancaria();
                autorizacao.setUsuario(usuarioOpt.get());
                autorizacao.setTipoBanco("MERCADO_PAGO");
                autorizacao.setBanco("Mercado Pago");
                autorizacao.setTipoConta("API");
                autorizacao.setAccessToken(accessToken);
                autorizacao.setTokenType(tokenType != null ? tokenType : "Bearer");
                autorizacao.setScope(scope);
                autorizacao.setDataCriacao(LocalDateTime.now());
                autorizacao.setDataAtualizacao(LocalDateTime.now());
                autorizacao.setDataExpiracao(dataExpiracao);
                autorizacao.setAtivo(true);
                log.info("✨ Criando nova autorização para usuário: {}", userId);
            }
            
            // Salvar no banco
            autorizacaoBancariaRepository.save(autorizacao);
            log.info("✅ Autorização bancária salva com sucesso para usuário: {}", userId);
            
        } catch (Exception e) {
            log.error("❌ Erro ao salvar autorização bancária para usuário {}: {}", userId, e.getMessage(), e);
        }
    }
>>>>>>> origin/main
}
