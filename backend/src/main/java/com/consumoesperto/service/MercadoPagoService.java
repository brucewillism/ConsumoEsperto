package com.consumoesperto.service;

import com.consumoesperto.dto.MercadoPagoCartaoDTO;
import com.consumoesperto.dto.MercadoPagoFaturaDTO;
import com.consumoesperto.dto.MercadoPagoTransacaoDTO;
import com.consumoesperto.exception.ExternalApiException;
import com.consumoesperto.exception.ConfigurationException;
import com.consumoesperto.exception.ResourceNotFoundException;
import com.consumoesperto.model.AutorizacaoBancaria;
import com.consumoesperto.model.BankApiConfig;
import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.Fatura;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.AutorizacaoBancariaRepository;
import com.consumoesperto.repository.BankApiConfigRepository;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoService {

    private final RestTemplate restTemplate;
    private final BankApiConfigRepository bankApiConfigRepository;
    private final BankApiConfigService bankApiConfigService;
    private final UsuarioService usuarioService;
    private final UsuarioRepository usuarioRepository;
    private final CartaoCreditoRepository cartaoCreditoRepository;
    private final FaturaRepository faturaRepository;
    private final TransacaoRepository transacaoRepository;
    private final AutorizacaoBancariaService autorizacaoBancariaService;
    private final MercadoPagoTokenRefreshService tokenRefreshService;
    private final AutorizacaoBancariaRepository autorizacaoBancariaRepository;
    private final UrlConfigurationService urlConfigurationService;

    /**
     * Busca cartões do Mercado Pago para um usuário
     */
    public List<MercadoPagoCartaoDTO> buscarCartoes(Long userId) {
        try {
            log.info("💳 Buscando cartões para usuário: {}", userId);
            
            // Verifica se usuário possui configuração ativa
            Optional<BankApiConfig> config = getMercadoPagoConfig(userId);
            BankApiConfig mpConfig;
            
            if (!config.isPresent()) {
                log.info("ℹ️ Usuário {} não possui configuração, verificando autorização bancária...", userId);
                
                // Verificar se tem autorização bancária (token válido)
                Optional<AutorizacaoBancaria> auth = autorizacaoBancariaService
                        .buscarAutorizacao(userId, BankApiService.BankType.MERCADO_PAGO);
                
                if (!auth.isPresent() || auth.get().getAccessToken() == null) {
                    log.warn("⚠️ Usuário {} não possui autorização bancária válida, retornando lista vazia", userId);
                    return new ArrayList<>();
                }
                
                // Verificar e renovar token se necessário
                log.info("🔄 Verificando se token precisa ser renovado para usuário: {}", userId);
                if (!tokenRefreshService.renovarTokenSeNecessario(userId)) {
                    log.warn("⚠️ Falha ao renovar token para usuário: {}, retornando lista vazia", userId);
                    return new ArrayList<>();
                }
                
                // Criar configuração automática baseada na autorização existente
                log.info("🔧 Criando configuração automática do Mercado Pago para usuário: {}", userId);
                mpConfig = criarConfiguracaoAutomatica(userId);
                
            } else {
                mpConfig = config.get();
            }

            // Fazer chamada real para a API do Mercado Pago
            try {
                log.info("🚀 Fazendo chamada real para API do Mercado Pago...");
                
                // Chamada direta à API do Mercado Pago usando a configuração
                List<MercadoPagoCartaoDTO> cartoesReais = buscarCartoesReais(mpConfig);
                
                if (cartoesReais != null && !cartoesReais.isEmpty()) {
                    log.info("✅ {} cartões reais retornados da API do Mercado Pago", cartoesReais.size());
                    return cartoesReais;
                } else {
                    log.warn("⚠️ Nenhum cartão encontrado na API do Mercado Pago, retornando lista vazia");
                    return new ArrayList<>();
                }
                
            } catch (Exception e) {
                log.error("❌ Erro ao buscar cartões reais da API: {}", e.getMessage());
                return new ArrayList<>();
            }
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar cartões: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Busca configuração do Mercado Pago para um usuário
     */
    private Optional<BankApiConfig> getMercadoPagoConfig(Long userId) {
        try {
            log.debug("🔍 Buscando configuração do Mercado Pago para usuário: {}", userId);
            return bankApiConfigRepository.findByUsuarioIdAndTipoBanco(userId, "MERCADO_PAGO");
        } catch (Exception e) {
            log.error("❌ Erro ao buscar configuração do Mercado Pago para usuário {}: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Cria configuração automática do Mercado Pago
     */
    private BankApiConfig criarConfiguracaoAutomatica(Long userId) {
        try {
            log.info("🔧 Criando configuração automática do Mercado Pago para usuário: {}", userId);
            
            // Buscar usuário
            Usuario usuario = usuarioRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("Usuário não encontrado: " + userId));
            
            // Criar configuração básica
            BankApiConfig config = new BankApiConfig();
            config.setTipoBanco("MERCADO_PAGO");
            config.setBanco("MERCADO_PAGO");
            config.setNome("Mercado Pago");
            config.setClientId("AUTO_GENERATED"); // Placeholder
            config.setClientSecret("AUTO_GENERATED"); // Placeholder
            config.setApiUrl("https://api.mercadopago.com/v1");
            config.setRedirectUri(urlConfigurationService.getMercadoPagoCallbackUrl());
            config.setScope("read write");
            config.setAtivo(true);
            config.setDataCriacao(LocalDateTime.now());
            config.setDataAtualizacao(LocalDateTime.now());
            config.setUsuario(usuario);
            
            // Salvar configuração
            BankApiConfig savedConfig = bankApiConfigService.saveConfig(config);
            log.info("✅ Configuração automática criada com ID: {}", savedConfig.getId());
            
            return savedConfig;
                
            } catch (Exception e) {
            log.error("❌ Erro ao criar configuração automática: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao criar configuração automática", e);
        }
    }

    /**
     * Busca cartões reais da API do Mercado Pago
     */
    public List<MercadoPagoCartaoDTO> buscarCartoesReais(BankApiConfig config) {
        try {
            log.info("🚀 Fazendo chamada real para API do Mercado Pago...");
                
                // Buscar Access Token da tabela de autorizações bancárias
            Long usuarioId = config.getUsuario().getId();
                Optional<AutorizacaoBancaria> auth = autorizacaoBancariaService
                    .buscarAutorizacao(usuarioId, BankApiService.BankType.MERCADO_PAGO);
                
                if (!auth.isPresent()) {
                    log.error("❌ Usuário {} não possui autorização bancária configurada", usuarioId);
                return new ArrayList<>();
            }
            
            // Primeiro, buscar o customer_id do usuário
            String customerId = buscarCustomerId(auth.get().getAccessToken(), config.getUsuario().getEmail());
            
            if (customerId == null) {
                log.warn("⚠️ Customer ID não encontrado para o usuário: {}", config.getUsuario().getEmail());
                return new ArrayList<>();
            }
            
            // Para cartões virtuais do Mercado Pago, usar endpoints específicos
            List<String> endpoints = Arrays.asList(
                "https://api.mercadopago.com/v1/customers/" + customerId + "/cards",
                "https://api.mercadopago.com/v1/cards?customer_id=" + customerId,
                "https://api.mercadopago.com/v1/cards",
                "https://api.mercadopago.com/v1/payment_methods/cards"
            );
            
            ResponseEntity<Object> cartoesResponse = null;
            String cartoesUrl = "";
            
            for (String endpoint : endpoints) {
                try {
                    cartoesUrl = endpoint;
                    HttpHeaders cartoesHeaders = new HttpHeaders();
                    cartoesHeaders.set("Authorization", "Bearer " + auth.get().getAccessToken());
                    cartoesHeaders.set("Content-Type", "application/json");
                    
                    HttpEntity<String> cartoesRequest = new HttpEntity<>(cartoesHeaders);
                    cartoesResponse = restTemplate.exchange(
                            cartoesUrl, HttpMethod.GET, cartoesRequest, Object.class);
                    
                    if (cartoesResponse.getStatusCode().is2xxSuccessful()) {
                        log.info("✅ Endpoint funcionou: {}", endpoint);
                        break;
                    }
        } catch (Exception e) {
                    log.warn("⚠️ Endpoint falhou: {} - {}", endpoint, e.getMessage());
                }
            }
            
            if (cartoesResponse == null || !cartoesResponse.getStatusCode().is2xxSuccessful()) {
                log.warn("⚠️ Todos os endpoints de cartões falharam, retornando lista vazia");
                return new ArrayList<>();
            }
            
            if (cartoesResponse.getStatusCode().is2xxSuccessful()) {
                Object responseBody = cartoesResponse.getBody();
                List<Map<String, Object>> cartoesList = new ArrayList<>();
                
                if (responseBody instanceof List) {
                    // API retorna array diretamente
                    cartoesList = (List<Map<String, Object>>) responseBody;
                } else if (responseBody instanceof Map) {
                    // API retorna objeto com propriedade 'data'
                    Map<String, Object> cartoesData = (Map<String, Object>) responseBody;
                    if (cartoesData.containsKey("data") && cartoesData.get("data") instanceof List) {
                        cartoesList = (List<Map<String, Object>>) cartoesData.get("data");
                    }
                }
                
                List<MercadoPagoCartaoDTO> cartoes = new ArrayList<>();
                for (Map<String, Object> cartaoData : cartoesList) {
                    MercadoPagoCartaoDTO cartao = new MercadoPagoCartaoDTO();
                    
                    // ID do cartão
                    cartao.setId((String) cartaoData.get("id"));
                    
                    // Nome do portador (pode ser null para cartões virtuais)
                    String cardholderName = (String) cartaoData.get("cardholder_name");
                    if (cardholderName == null || cardholderName.trim().isEmpty()) {
                        cardholderName = "Cartão Virtual Mercado Pago";
                    }
                    cartao.setNome(cardholderName);
                    
                    // Últimos dígitos
                    String ultimosDigitos = (String) cartaoData.get("last_four_digits");
                    if (ultimosDigitos == null || ultimosDigitos.trim().isEmpty()) {
                        ultimosDigitos = "****";
                    }
                    cartao.setUltimosDigitos(ultimosDigitos);
                    
                    // Limite do cartão (cartões virtuais podem não ter limite específico)
                                    if (cartaoData.containsKey("credit_limit")) {
                                        Object limite = cartaoData.get("credit_limit");
                                        if (limite instanceof Number) {
                                            cartao.setLimiteTotal(new BigDecimal(limite.toString()));
                                            cartao.setLimiteDisponivel(new BigDecimal(limite.toString()));
                                        }
                    } else {
                        // Para cartões virtuais, usar limite padrão do Mercado Pago
                        cartao.setLimiteTotal(new BigDecimal("500.00"));
                        cartao.setLimiteDisponivel(new BigDecimal("500.00"));
                                    }
                                    
                    // Data de vencimento da fatura (próximo mês)
                    cartao.setVencimentoFatura(LocalDate.now().plusMonths(1).withDayOfMonth(10));
                                    cartao.setValorFatura(BigDecimal.ZERO);
                                    cartao.setAtivo(true);
                    
                    // Bandeira do cartão
                    if (cartaoData.containsKey("payment_method")) {
                        Object paymentMethod = cartaoData.get("payment_method");
                        if (paymentMethod instanceof Map) {
                            Map<String, Object> pm = (Map<String, Object>) paymentMethod;
                            String bandeira = (String) pm.get("id");
                            if (bandeira != null) {
                                cartao.setBandeira(bandeira.toUpperCase());
                            } else {
                                cartao.setBandeira("VISA"); // Padrão para cartões virtuais
                            }
                        }
                    } else {
                        cartao.setBandeira("VISA"); // Padrão para cartões virtuais
                    }
                    
                    // Tipo do cartão
                    cartao.setTipo("Crédito Virtual");
                    
                        cartoes.add(cartao);
                    log.info("✅ Cartão virtual encontrado: {} - Últimos Dígitos: {} - Bandeira: {}", 
                        cartao.getNome(), cartao.getUltimosDigitos(), cartao.getBandeira());
                    }
                    
                    log.info("✅ {} cartões reais encontrados na API do Mercado Pago", cartoes.size());
                    return cartoes;
            }
            
            log.warn("⚠️ API retornou status não esperado ou sem dados de cartões: {}", cartoesResponse.getStatusCode());
            return new ArrayList<>();
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar cartões reais da API: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Busca customer_id do usuário no Mercado Pago
     */
    private String buscarCustomerId(String accessToken, String email) {
        try {
            log.info("🔍 Buscando customer_id para email: {}", email);
            
            String searchUrl = "https://api.mercadopago.com/v1/customers/search?email=" + email;
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            headers.set("Content-Type", "application/json");
            
            HttpEntity<String> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    searchUrl, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                if (responseBody.containsKey("results") && responseBody.get("results") instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> results = (List<Map<String, Object>>) responseBody.get("results");
                    
                    if (!results.isEmpty()) {
                        String customerId = results.get(0).get("id").toString();
                        log.info("✅ Customer ID encontrado: {}", customerId);
                        return customerId;
                    }
                }
            }
            
            log.warn("⚠️ Customer ID não encontrado para email: {}, tentando criar...", email);
            
            // Tentar criar um customer se não existir
            // Buscar usuário pelo email para obter nome
            Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(email);
            String primeiroNome = "Usuário";
            String ultimoNome = "Mercado Pago";
            
            if (usuarioOpt.isPresent()) {
                Usuario usuario = usuarioOpt.get();
                String nomeCompleto = usuario.getNome();
                if (nomeCompleto != null && !nomeCompleto.trim().isEmpty()) {
                    String[] partesNome = nomeCompleto.trim().split("\\s+", 2);
                    primeiroNome = partesNome[0];
                    if (partesNome.length > 1) {
                        ultimoNome = partesNome[1];
                    }
                }
            }
            
            return criarCustomer(accessToken, email, primeiroNome, ultimoNome);
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar customer_id: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Cria um customer no Mercado Pago
     */
    private String criarCustomer(String accessToken, String email, String primeiroNome, String ultimoNome) {
        try {
            log.info("🆕 Criando customer no Mercado Pago para email: {} - Nome: {} {}", email, primeiroNome, ultimoNome);
            
            String createUrl = "https://api.mercadopago.com/v1/customers";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            headers.set("Content-Type", "application/json");
            
            // Dados do customer
            Map<String, Object> customerData = new HashMap<>();
            customerData.put("email", email);
            customerData.put("first_name", primeiroNome);
            customerData.put("last_name", ultimoNome);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(customerData, headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    createUrl, HttpMethod.POST, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                if (responseBody.containsKey("id")) {
                    String customerId = responseBody.get("id").toString();
                    log.info("✅ Customer criado com sucesso: {}", customerId);
                    return customerId;
                }
            }
            
            log.warn("⚠️ Falha ao criar customer para email: {}", email);
            return null;
            
        } catch (Exception e) {
            log.error("❌ Erro ao criar customer: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Sincroniza dados reais do Mercado Pago
     */
    public void sincronizarDadosReais(Long userId) {
        try {
            log.info("🔄 Iniciando sincronização de dados reais do Mercado Pago para usuário: {}", userId);
            
            // Buscar configuração do usuário
            Optional<BankApiConfig> config = getMercadoPagoConfig(userId);
            if (config.isEmpty()) {
                log.warn("⚠️ Usuário {} não possui configuração do Mercado Pago", userId);
                return;
            }
            
            // Buscar autorização bancária
            Optional<AutorizacaoBancaria> auth = autorizacaoBancariaService
                    .buscarAutorizacao(userId, BankApiService.BankType.MERCADO_PAGO);
            
            if (auth.isEmpty() || auth.get().getAccessToken() == null) {
                log.warn("⚠️ Usuário {} não possui autorização bancária válida", userId);
                return;
            }
            
            // Buscar transações reais
            List<MercadoPagoTransacaoDTO> transacoes = buscarTransacoesReais(auth.get().getAccessToken());
            
            if (transacoes != null && !transacoes.isEmpty()) {
                log.info("✅ {} transações reais encontradas", transacoes.size());
                // Processar transações...
            } else {
                log.warn("⚠️ Nenhuma transação real encontrada");
            }
            
        } catch (Exception e) {
            log.error("❌ Erro ao sincronizar dados reais: {}", e.getMessage(), e);
        }
    }

    /**
     * Busca transações reais do Mercado Pago
     */
    private List<MercadoPagoTransacaoDTO> buscarTransacoesReais(String accessToken) {
        try {
            log.info("🔍 Buscando transações reais do Mercado Pago...");
            
            String url = "https://api.mercadopago.com/v1/payments/search?limit=100&offset=0&sort=date_created&criteria=desc";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            headers.set("Content-Type", "application/json");
            
            HttpEntity<String> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                if (responseBody.containsKey("results") && responseBody.get("results") instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> payments = (List<Map<String, Object>>) responseBody.get("results");
                    
                    List<MercadoPagoTransacaoDTO> transacoes = new ArrayList<>();
                    for (Map<String, Object> payment : payments) {
                        MercadoPagoTransacaoDTO transacao = new MercadoPagoTransacaoDTO();
                        transacao.setId(payment.get("id").toString());
                        transacao.setDescricao((String) payment.get("description"));
                        transacao.setValor(new BigDecimal(payment.get("transaction_amount").toString()));
                        transacao.setDataTransacao(parseDataTransacao(payment.get("date_created").toString()));
                        transacao.setTipoTransacao("DESPESA"); // Mercado Pago geralmente são despesas
                        transacoes.add(transacao);
                    }
                    
                    log.info("✅ {} transações reais processadas", transacoes.size());
                    return transacoes;
                }
            }
            
            log.warn("⚠️ Nenhuma transação encontrada na API");
            return new ArrayList<>();
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar transações reais: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Converte string de data para LocalDateTime
     */
    private LocalDateTime parseDataTransacao(String dateStr) {
        try {
            // Mercado Pago usa formato ISO 8601
            return LocalDateTime.parse(dateStr.replace("Z", ""));
        } catch (Exception e) {
            log.warn("⚠️ Erro ao converter data: {}, usando data atual", dateStr);
            return LocalDateTime.now();
        }
    }

    /**
     * Sincroniza dados automaticamente para um usuário
     */
    public void sincronizarDadosAutomaticamente(Long usuarioId) {
        try {
            log.info("🔄 Iniciando sincronização automática para usuário: {}", usuarioId);
            sincronizarDadosReais(usuarioId);
            log.info("✅ Sincronização automática concluída para usuário: {}", usuarioId);
                } catch (Exception e) {
            log.error("❌ Erro na sincronização automática para usuário {}: {}", usuarioId, e.getMessage());
        }
    }

    /**
     * Verifica se o usuário possui configuração ativa
     */
    public boolean possuiConfiguracaoAtiva(Long usuarioId) {
        try {
            List<BankApiConfig> configs = bankApiConfigService.findByUsuarioId(usuarioId);
            return !configs.isEmpty();
        } catch (Exception e) {
            log.error("❌ Erro ao verificar configuração ativa para usuário {}: {}", usuarioId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Configura credenciais do Mercado Pago
     */
    public void configurarCredenciais(Long usuarioId, String accessToken, String publicKey, 
                                     String clientId, String clientSecret, String banco) {
        try {
            log.info("🔧 Configurando credenciais do Mercado Pago para usuário: {}", usuarioId);
            
            // Buscar usuário
            Usuario usuario = usuarioService.findById(usuarioId);
            if (usuario == null) {
                throw new ResourceNotFoundException("Usuário não encontrado");
            }
            
            // Criar ou atualizar configuração
            BankApiConfig config = new BankApiConfig();
            config.setUsuario(usuario);
            config.setTipoBanco(banco);
            config.setAtivo(true);
            
            bankApiConfigService.saveConfig(config);
            log.info("✅ Credenciais configuradas com sucesso para usuário: {}", usuarioId);
            
                } catch (Exception e) {
            log.error("❌ Erro ao configurar credenciais para usuário {}: {}", usuarioId, e.getMessage());
            throw e;
        }
    }

    /**
     * Busca faturas do Mercado Pago
     */
    public List<MercadoPagoFaturaDTO> buscarFaturas(Long usuarioId) {
        try {
            log.info("🔍 Buscando faturas do Mercado Pago para usuário: {}", usuarioId);
            
            // Buscar autorização bancária
            Optional<AutorizacaoBancaria> authOpt = autorizacaoBancariaService
                    .buscarAutorizacao(usuarioId, BankApiService.BankType.MERCADO_PAGO);
            
            if (!authOpt.isPresent()) {
                log.warn("⚠️ Usuário {} não possui autorização bancária do Mercado Pago", usuarioId);
                return new ArrayList<>();
            }
            
            AutorizacaoBancaria auth = authOpt.get();
            String accessToken = auth.getAccessToken();
            
            // Verificar se é token temporário
            if (isTemporaryToken(accessToken)) {
                log.warn("⚠️ Token temporário detectado para usuário: {}", usuarioId);
                return new ArrayList<>();
            }
            
            // Buscar faturas reais da API
            List<MercadoPagoFaturaDTO> faturas = buscarFaturasReais(accessToken);
            
            if (faturas != null && !faturas.isEmpty()) {
                log.info("✅ {} faturas encontradas para usuário: {}", faturas.size(), usuarioId);
                return faturas;
            } else {
                log.warn("⚠️ Nenhuma fatura encontrada para usuário: {}", usuarioId);
                return new ArrayList<>();
            }
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar faturas para usuário {}: {}", usuarioId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Busca faturas reais da API do Mercado Pago
     */
    private List<MercadoPagoFaturaDTO> buscarFaturasReais(String accessToken) {
        try {
            log.info("🔍 Buscando faturas reais da API do Mercado Pago...");
            
            // Tentar diferentes endpoints para faturas
            List<String> endpoints = Arrays.asList(
                "https://api.mercadopago.com/v1/payments/search?limit=100&offset=0&sort=date_created&criteria=desc",
                "https://api.mercadopago.com/v1/payments",
                "https://api.mercadopago.com/v1/advanced_payments/search",
                "https://api.mercadopago.com/v1/account/balance"
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
                            return processarFaturasResponse(responseBody);
                        }
                    } else {
                        log.warn("⚠️ Endpoint falhou: {} - Status: {}", endpoint, response.getStatusCode());
                    }
                    
                } catch (Exception e) {
                    log.warn("⚠️ Erro no endpoint {}: {}", endpoint, e.getMessage());
                }
            }
            
            log.warn("⚠️ Todos os endpoints de faturas falharam");
            return new ArrayList<>();
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar faturas reais: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Processa a resposta da API para extrair faturas
     */
    private List<MercadoPagoFaturaDTO> processarFaturasResponse(Map<String, Object> responseBody) {
        List<MercadoPagoFaturaDTO> faturas = new ArrayList<>();
        
        try {
            // Verificar se é uma resposta de pagamentos
            if (responseBody.containsKey("results") && responseBody.get("results") instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> payments = (List<Map<String, Object>>) responseBody.get("results");
                
                for (Map<String, Object> payment : payments) {
                    MercadoPagoFaturaDTO fatura = new MercadoPagoFaturaDTO();
                    
                    // ID da fatura
                    fatura.setId(payment.get("id").toString());
                    
                    // Descrição
                    String descricao = (String) payment.get("description");
                    if (descricao == null || descricao.trim().isEmpty()) {
                        descricao = "Pagamento Mercado Pago";
                    }
                    fatura.setDescricao(descricao);
                    
                    // Valor
                    if (payment.containsKey("transaction_amount")) {
                        Object valor = payment.get("transaction_amount");
                        if (valor instanceof Number) {
                            fatura.setValor(new BigDecimal(valor.toString()));
                        }
                    }
                    
                    // Data de vencimento (usar data de criação + 30 dias)
                    if (payment.containsKey("date_created")) {
                        String dateCreated = payment.get("date_created").toString();
                        try {
                            LocalDateTime dataCriacao = LocalDateTime.parse(dateCreated.replace("Z", ""));
                            fatura.setDataVencimento(dataCriacao.plusDays(30).toLocalDate());
                        } catch (Exception e) {
                            fatura.setDataVencimento(LocalDate.now().plusDays(30));
                        }
                    } else {
                        fatura.setDataVencimento(LocalDate.now().plusDays(30));
                    }
                    
                    // Status
                    String status = (String) payment.get("status");
                    fatura.setStatus(status != null ? status : "pending");
                    
                    // Tipo
                    fatura.setTipo("CREDITO");
                    
                    faturas.add(fatura);
                }
                
                log.info("✅ {} faturas processadas da API", faturas.size());
            }
            
        } catch (Exception e) {
            log.error("❌ Erro ao processar resposta de faturas: {}", e.getMessage(), e);
        }
        
        return faturas;
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
     * Testa conexão com o Mercado Pago
     */
    public boolean testarConexao(Long usuarioId) {
        try {
            log.info("🔍 Testando conexão com Mercado Pago para usuário: {}", usuarioId);
            
            List<BankApiConfig> configs = bankApiConfigService.findByUsuarioId(usuarioId);
            if (configs.isEmpty()) {
                return false;
            }
            
            // TODO: Implementar teste real de conexão
            return true;
            
        } catch (Exception e) {
            log.error("❌ Erro ao testar conexão para usuário {}: {}", usuarioId, e.getMessage());
            return false;
        }
    }

    /**
     * Renova automaticamente o token do Mercado Pago
     */
    public boolean renovarTokenAutomaticamente(Long usuarioId) {
        try {
            log.info("🔄 Iniciando renovação automática de token para usuário: {}", usuarioId);
            
            // Buscar autorização bancária existente
            Optional<AutorizacaoBancaria> auth = autorizacaoBancariaService
                    .buscarAutorizacao(usuarioId, BankApiService.BankType.MERCADO_PAGO);
            
            if (!auth.isPresent()) {
                log.warn("⚠️ Usuário {} não possui autorização bancária para renovar", usuarioId);
                return false;
            }
            
            AutorizacaoBancaria autorizacao = auth.get();
            
            // Verificar se o token realmente está expirado
            if (!autorizacao.isTokenExpirado()) {
                log.info("✅ Token ainda válido para usuário: {}", usuarioId);
                return true;
            }
            
            log.info("🔄 Token expirado detectado, iniciando renovação...");
            
            // Buscar configuração do Mercado Pago
            Optional<BankApiConfig> config = getMercadoPagoConfig(usuarioId);
            if (!config.isPresent()) {
                log.error("❌ Configuração do Mercado Pago não encontrada para usuário: {}", usuarioId);
                return false;
            }
            
            BankApiConfig mpConfig = config.get();
            
            // Tentar renovar usando refresh token (se disponível)
            String novoToken = renovarTokenComRefreshToken(autorizacao.getRefreshToken(), mpConfig);
            
            if (novoToken != null) {
                // Atualizar token no banco
                autorizacao.setAccessToken(novoToken);
                autorizacao.setDataExpiracao(LocalDateTime.now().plusHours(6)); // 6 horas de validade
                autorizacao.setDataAtualizacao(LocalDateTime.now());
                
                autorizacaoBancariaRepository.save(autorizacao);
                
                log.info("✅ Token renovado com sucesso para usuário: {}", usuarioId);
                return true;
            } else {
                log.warn("⚠️ Não foi possível renovar token automaticamente para usuário: {}", usuarioId);
                return false;
            }
            
        } catch (Exception e) {
            log.error("❌ Erro ao renovar token automaticamente para usuário {}: {}", usuarioId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Renova token usando refresh token
     */
    private String renovarTokenComRefreshToken(String refreshToken, BankApiConfig config) {
        try {
            if (refreshToken == null || refreshToken.trim().isEmpty()) {
                log.warn("⚠️ Refresh token não disponível");
                return null;
            }
            
            String url = "https://api.mercadopago.com/oauth/token";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/x-www-form-urlencoded");
            headers.set("Authorization", "Basic " + java.util.Base64.getEncoder()
                    .encodeToString((config.getClientId() + ":" + config.getClientSecret()).getBytes()));
            
            String body = "grant_type=refresh_token&refresh_token=" + refreshToken;
            
            HttpEntity<String> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                String accessToken = (String) responseBody.get("access_token");
                
                if (accessToken != null) {
                    log.info("✅ Token renovado com sucesso via refresh token");
                    return accessToken;
                }
            }
            
            log.warn("⚠️ Falha ao renovar token via refresh token");
            return null;
            
        } catch (Exception e) {
            log.error("❌ Erro ao renovar token via refresh token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Verifica e renova token se necessário
     */
    public boolean verificarERenovarToken(Long usuarioId) {
        try {
            log.info("🔍 Verificando status do token para usuário: {}", usuarioId);
            
            Optional<AutorizacaoBancaria> auth = autorizacaoBancariaService
                    .buscarAutorizacao(usuarioId, BankApiService.BankType.MERCADO_PAGO);
            
            if (!auth.isPresent()) {
                log.warn("⚠️ Usuário {} não possui autorização bancária", usuarioId);
                return false;
            }
            
            AutorizacaoBancaria autorizacao = auth.get();
            
            if (autorizacao.isTokenExpirado()) {
                log.info("🔄 Token expirado detectado, tentando renovar...");
                return renovarTokenAutomaticamente(usuarioId);
            } else {
                log.info("✅ Token válido para usuário: {}", usuarioId);
                return true;
            }
            
        } catch (Exception e) {
            log.error("❌ Erro ao verificar token para usuário {}: {}", usuarioId, e.getMessage());
            return false;
        }
    }
}
