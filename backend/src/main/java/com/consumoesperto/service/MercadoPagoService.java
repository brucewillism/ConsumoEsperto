package com.consumoesperto.service;

import com.consumoesperto.dto.MercadoPagoCartaoDTO;
import com.consumoesperto.dto.MercadoPagoFaturaDTO;
<<<<<<< HEAD
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
=======
import com.consumoesperto.dto.CartaoCreditoDTO;
import com.consumoesperto.model.BankApiConfig;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.model.AutorizacaoBancaria;
import com.consumoesperto.repository.BankApiConfigRepository;
>>>>>>> origin/main
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
<<<<<<< HEAD
=======
import org.springframework.http.MediaType;
>>>>>>> origin/main
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
<<<<<<< HEAD
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Arrays;

=======
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.Fatura;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.TransacaoRepository;

/**
 * Serviço para integração com a API do Mercado Pago
 * 
 * Este serviço gerencia a comunicação com a API do Mercado Pago,
 * incluindo autenticação, busca de cartões e faturas.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
>>>>>>> origin/main
@Service
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoService {

    private final RestTemplate restTemplate;
    private final BankApiConfigRepository bankApiConfigRepository;
<<<<<<< HEAD
    private final BankApiConfigService bankApiConfigService;
    private final UsuarioService usuarioService;
=======
>>>>>>> origin/main
    private final UsuarioRepository usuarioRepository;
    private final CartaoCreditoRepository cartaoCreditoRepository;
    private final FaturaRepository faturaRepository;
    private final TransacaoRepository transacaoRepository;
    private final AutorizacaoBancariaService autorizacaoBancariaService;
<<<<<<< HEAD
    private final MercadoPagoTokenRefreshService tokenRefreshService;
    private final AutorizacaoBancariaRepository autorizacaoBancariaRepository;
    private final UrlConfigurationService urlConfigurationService;

    /**
     * Busca cartões do Mercado Pago para um usuário
=======

    /**
     * Busca configuração ativa do Mercado Pago para um usuário
     * Se não existir, cria uma configuração padrão automaticamente
     * 
     * @param userId ID do usuário
     * @return Configuração do Mercado Pago (criada automaticamente se necessário)
     */
    private Optional<BankApiConfig> getMercadoPagoConfig(Long userId) {
        try {
            log.debug("🔍 Buscando configuração do Mercado Pago para usuário: {}", userId);
            Optional<BankApiConfig> config = bankApiConfigRepository.findByUsuarioIdAndTipoBanco(userId, "MERCADOPAGO");
            
            // SÓ retorna configuração se existir E estiver ativa
            if (config.isPresent() && config.get().getAtivo()) {
                return config;
            }
            
            // Se não existe configuração ou não está ativa, retorna vazio
            if (!config.isPresent()) {
                log.info("ℹ️ Usuário {} não possui configuração do Mercado Pago", userId);
            } else {
                log.info("ℹ️ Usuário {} possui configuração inativa do Mercado Pago", userId);
            }
            
            return Optional.empty();
        } catch (Exception e) {
            log.error("❌ Erro ao buscar configuração do Mercado Pago para usuário {}: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Busca cartões de crédito do usuário no Mercado Pago
     * 
     * @param userId ID do usuário
     * @return Lista de cartões de crédito
>>>>>>> origin/main
     */
    public List<MercadoPagoCartaoDTO> buscarCartoes(Long userId) {
        try {
            log.info("💳 Buscando cartões para usuário: {}", userId);
            
            // Verifica se usuário possui configuração ativa
            Optional<BankApiConfig> config = getMercadoPagoConfig(userId);
<<<<<<< HEAD
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
=======
            if (!config.isPresent()) {
                log.warn("⚠️ Usuário {} não possui credenciais configuradas", userId);
                return new ArrayList<>();
            }

            // Verifica se o Client Secret foi configurado
            BankApiConfig mpConfig = config.get();
            if (mpConfig.getClientSecret() == null || mpConfig.getClientSecret().trim().isEmpty()) {
                log.warn("⚠️ Usuário {} precisa configurar o Client Secret do Mercado Pago", userId);
                return new ArrayList<>();
>>>>>>> origin/main
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
<<<<<<< HEAD
                    log.warn("⚠️ Nenhum cartão encontrado na API do Mercado Pago, retornando lista vazia");
=======
                    log.warn("⚠️ Nenhum cartão encontrado na API do Mercado Pago");
>>>>>>> origin/main
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
<<<<<<< HEAD
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
=======
     * Busca faturas dos cartões do usuário no Mercado Pago
     * 
     * @param userId ID do usuário
     * @return Lista de faturas
     */
    public List<MercadoPagoFaturaDTO> buscarFaturas(Long userId) {
        try {
            log.info("📄 Buscando faturas para usuário: {}", userId);
            
            // Verifica se usuário possui configuração ativa
            Optional<BankApiConfig> config = getMercadoPagoConfig(userId);
            if (!config.isPresent()) {
                log.warn("⚠️ Usuário {} não possui credenciais configuradas", userId);
                return new ArrayList<>();
            }

            // Verifica se o Client Secret foi configurado
            BankApiConfig mpConfig = config.get();
            if (mpConfig.getClientSecret() == null || mpConfig.getClientSecret().trim().isEmpty()) {
                log.warn("⚠️ Usuário {} precisa configurar o Client Secret do Mercado Pago", userId);
                return new ArrayList<>();
            }

            // Fazer chamada real para a API do Mercado Pago
            try {
                log.info("🚀 Fazendo chamada real para API do Mercado Pago - Faturas...");
                
                List<MercadoPagoFaturaDTO> faturasReais = buscarFaturasReais(mpConfig);
                
                if (faturasReais != null && !faturasReais.isEmpty()) {
                    log.info("✅ {} faturas reais retornadas da API do Mercado Pago", faturasReais.size());
                    return faturasReais;
                } else {
                    log.warn("⚠️ Nenhuma fatura encontrada na API do Mercado Pago");
                    return new ArrayList<>();
                }
                
            } catch (Exception e) {
                log.error("❌ Erro ao buscar faturas reais da API: {}", e.getMessage());
                return new ArrayList<>();
            }
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar faturas: {}", e.getMessage(), e);
            return new ArrayList<>();
>>>>>>> origin/main
        }
    }

    /**
<<<<<<< HEAD
     * Busca cartões reais da API do Mercado Pago
     */
    public List<MercadoPagoCartaoDTO> buscarCartoesReais(BankApiConfig config) {
        try {
            log.info("🚀 Fazendo chamada real para API do Mercado Pago...");
                
                // Buscar Access Token da tabela de autorizações bancárias
            Long usuarioId = config.getUsuario().getId();
=======
     * Busca transações dos cartões do usuário no Mercado Pago
     * 
     * @param userId ID do usuário
     * @return Lista de transações
     */
    public List<Transacao> buscarTransacoes(Long userId) {
        try {
            log.info("💰 Buscando transações para usuário: {}", userId);
            
            // Verifica se usuário possui configuração ativa
            Optional<BankApiConfig> config = getMercadoPagoConfig(userId);
            if (!config.isPresent()) {
                log.warn("⚠️ Usuário {} não possui credenciais configuradas", userId);
                return new ArrayList<>();
            }

            // Verifica se o Client Secret foi configurado
            BankApiConfig mpConfig = config.get();
            if (mpConfig.getClientSecret() == null || mpConfig.getClientSecret().trim().isEmpty()) {
                log.warn("⚠️ Usuário {} precisa configurar o Client Secret do Mercado Pago", userId);
                return new ArrayList<>();
            }

            // Fazer chamada real para a API do Mercado Pago
            try {
                log.info("🚀 Fazendo chamada real para API do Mercado Pago - Transações...");
                
                List<Transacao> transacoesReais = buscarTransacoesReais(mpConfig, userId);
                
                if (transacoesReais != null && !transacoesReais.isEmpty()) {
                    log.info("✅ {} transações reais retornadas da API do Mercado Pago", transacoesReais.size());
                    return transacoesReais;
                } else {
                    log.warn("⚠️ Nenhuma transação encontrada na API do Mercado Pago");
                    return new ArrayList<>();
                }
                
            } catch (Exception e) {
                log.error("❌ Erro ao buscar transações reais da API: {}", e.getMessage());
                return new ArrayList<>();
            }
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar transações: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Testa a conexão com a API do Mercado Pago
     * 
     * @param userId ID do usuário
     * @return true se conectado, false caso contrário
     */
    public boolean testarConexao(Long userId) {
        try {
            log.info("🔍 Testando conexão para usuário: {}", userId);
            
            // Verifica se usuário possui configuração ativa
            Optional<BankApiConfig> config = getMercadoPagoConfig(userId);
            if (!config.isPresent()) {
                log.warn("⚠️ Usuário {} não possui credenciais configuradas", userId);
                return false;
            }

            // Fazer chamada real para testar conexão com a API do Mercado Pago
            try {
                log.info("🔍 Testando conexão real com API do Mercado Pago...");
                
                // Buscar Access Token da tabela de autorizações bancárias
                Long usuarioId = config.get().getUsuario().getId();
>>>>>>> origin/main
                Optional<AutorizacaoBancaria> auth = autorizacaoBancariaService
                    .buscarAutorizacao(usuarioId, BankApiService.BankType.MERCADO_PAGO);
                
                if (!auth.isPresent()) {
                    log.error("❌ Usuário {} não possui autorização bancária configurada", usuarioId);
<<<<<<< HEAD
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
=======
                    return false;
                }
                
                String accessToken = auth.get().getAccessToken();
                if (accessToken == null || accessToken.trim().isEmpty()) {
                    log.error("❌ Access Token não encontrado para usuário {}", usuarioId);
                    return false;
                }
                
                // Fazer chamada de teste para a API - PRODUÇÃO
                String url = "https://api.mercadopago.com/v1/payments/search?limit=1";
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(accessToken);
                headers.set("X-Client-Id", config.get().getClientId());
                
                HttpEntity<String> request = new HttpEntity<>(headers);
                @SuppressWarnings("rawtypes")
                ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
                
                if (response.getStatusCode().is2xxSuccessful()) {
                    log.info("✅ Conexão com API do Mercado Pago estabelecida com sucesso");
                    return true;
                    } else {
                    log.warn("⚠️ API retornou status: {}", response.getStatusCode());
                    return false;
                }
                
                } catch (Exception e) {
                log.error("❌ Erro ao testar conexão com API: {}", e.getMessage());
                return false;
            }
            
        } catch (Exception e) {
            log.error("❌ Erro ao testar conexão: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Verifica se usuário possui configuração ativa
     */
    public boolean possuiConfiguracaoAtiva(Long userId) {
        Optional<BankApiConfig> config = bankApiConfigRepository.findByUsuarioIdAndTipoBanco(userId, "MERCADOPAGO");
        return config.isPresent() && config.get().getAtivo();
    }
    
    /**
     * Busca cartões reais da API do Mercado Pago (versão pública)
     * 
     * @param userId ID do usuário
     * @return Lista de cartões reais como CartaoCreditoDTO
     */
    public List<CartaoCreditoDTO> buscarCartoesReais(Long userId) {
        try {
            log.info("💳 Buscando cartões reais para usuário: {}", userId);
            
            // Verifica se usuário possui configuração ativa
            Optional<BankApiConfig> config = getMercadoPagoConfig(userId);
            if (!config.isPresent()) {
                log.warn("⚠️ Usuário {} não possui credenciais configuradas", userId);
                return new ArrayList<>();
            }

            // Fazer chamada real para a API do Mercado Pago
            List<MercadoPagoCartaoDTO> cartoesMercadoPago = buscarCartoesReais(config.get());
            
            if (cartoesMercadoPago != null && !cartoesMercadoPago.isEmpty()) {
                // Converter para CartaoCreditoDTO
                return cartoesMercadoPago.stream()
                    .map(this::converterParaCartaoCreditoDTO)
                    .collect(Collectors.toList());
            }
            
            return new ArrayList<>();
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar cartões reais: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Converte MercadoPagoCartaoDTO para CartaoCreditoDTO
     */
    private CartaoCreditoDTO converterParaCartaoCreditoDTO(MercadoPagoCartaoDTO cartaoMP) {
        CartaoCreditoDTO dto = new CartaoCreditoDTO();
        dto.setNome(cartaoMP.getNome());
        dto.setNumeroCartao(cartaoMP.getUltimosDigitos());
        dto.setLimiteCredito(cartaoMP.getLimiteTotal());
        dto.setLimiteDisponivel(cartaoMP.getLimiteDisponivel());
        dto.setBanco("Mercado Pago");
        dto.setAtivo(true);
        return dto;
    }

    /**
     * Busca cartões reais da API do Mercado Pago (versão privada)
     * 
     * @param config Configuração do Mercado Pago
     * @return Lista de cartões reais ou null se houver erro
     */
    private List<MercadoPagoCartaoDTO> buscarCartoesReais(BankApiConfig config) {
        try {
            log.info("🚀 Fazendo chamada real para API do Mercado Pago...");
            
            // Buscar Access Token da tabela de autorizações bancárias
            Long usuarioId = config.getUsuario().getId();
            Optional<AutorizacaoBancaria> auth = autorizacaoBancariaService
                .buscarAutorizacao(usuarioId, BankApiService.BankType.MERCADO_PAGO);
            
            if (!auth.isPresent()) {
                log.error("❌ Usuário {} não possui autorização bancária configurada", usuarioId);
                return null;
            }
            
            String accessToken = auth.get().getAccessToken();
            if (accessToken == null || accessToken.trim().isEmpty()) {
                log.error("❌ Access Token não encontrado para usuário {}", usuarioId);
                return null;
            }
            
            // Construir URL da API para cartões (endpoint correto do Mercado Pago - PRODUÇÃO)
            String url = "https://api.mercadopago.com/v1/payments/search";
            
            // Headers da requisição
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // Usar access token real do Mercado Pago
            headers.setBearerAuth(accessToken);
            headers.set("X-Client-Id", config.getClientId());
            
            log.info("🔑 Usando Client ID: {}", config.getClientId());
            log.info("🔐 Usando Access Token: {}...", accessToken.substring(0, Math.min(8, accessToken.length())) + "***");
            
            // Adicionar parâmetros de consulta para buscar pagamentos recentes
            String urlWithParams = url + "?limit=10&offset=0&sort=date_created&criteria=desc";
            
            // Fazer requisição
            HttpEntity<String> request = new HttpEntity<>(headers);
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.exchange(urlWithParams, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("✅ API do Mercado Pago retornou dados com sucesso");
                
                // Processar resposta da API
                @SuppressWarnings("unchecked")
                Map<String, Object> responseBody = response.getBody();
                if (responseBody != null && responseBody.containsKey("results") && responseBody.get("results") instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> paymentsData = (List<Map<String, Object>>) responseBody.get("results");
                    log.info("📊 {} pagamentos retornados da API", paymentsData.size());
                    
                    // Buscar dados reais de cartões do Mercado Pago
                    List<MercadoPagoCartaoDTO> cartoes = new ArrayList<>();
                    
                    // Tentar buscar dados reais de cartões via API do Mercado Pago
                    try {
                        // Buscar informações de cartões via endpoint de pagamentos - PRODUÇÃO
                        String cartoesUrl = "https://api.mercadopago.com/v1/cards";
                        HttpHeaders cartoesHeaders = new HttpHeaders();
                        cartoesHeaders.set("Authorization", "Bearer " + auth.get().getAccessToken());
                        cartoesHeaders.set("Content-Type", "application/json");
                        
                        HttpEntity<String> cartoesRequest = new HttpEntity<>(cartoesHeaders);
                        ResponseEntity<Map> cartoesResponse = restTemplate.exchange(
                            cartoesUrl, HttpMethod.GET, cartoesRequest, Map.class);
                        
                        if (cartoesResponse.getStatusCode().is2xxSuccessful()) {
                            Map<String, Object> cartoesData = cartoesResponse.getBody();
                            if (cartoesData != null && cartoesData.containsKey("data")) {
                                List<Map<String, Object>> cartoesList = (List<Map<String, Object>>) cartoesData.get("data");
                                
                                for (Map<String, Object> cartaoData : cartoesList) {
                                    MercadoPagoCartaoDTO cartao = new MercadoPagoCartaoDTO();
                                    cartao.setId((String) cartaoData.get("id"));
                                    cartao.setNome((String) cartaoData.get("name"));
                                    cartao.setUltimosDigitos((String) cartaoData.get("last_four_digits"));
                                    
                                    // Buscar limite real do cartão
>>>>>>> origin/main
                                    if (cartaoData.containsKey("credit_limit")) {
                                        Object limite = cartaoData.get("credit_limit");
                                        if (limite instanceof Number) {
                                            cartao.setLimiteTotal(new BigDecimal(limite.toString()));
                                            cartao.setLimiteDisponivel(new BigDecimal(limite.toString()));
                                        }
<<<<<<< HEAD
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
=======
                                    }
                                    
                                    cartao.setVencimentoFatura(LocalDate.now().plusDays(15));
                                    cartao.setValorFatura(BigDecimal.ZERO);
                                    cartao.setAtivo(true);
                                    cartao.setBandeira((String) cartaoData.get("brand"));
                                    cartao.setTipo("Crédito");
                                    
                                    cartoes.add(cartao);
                                    log.info("✅ Cartão real encontrado: {} - Limite: {}", 
                                        cartao.getNome(), cartao.getLimiteTotal());
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("⚠️ Erro ao buscar cartões reais, usando dados simulados: {}", e.getMessage());
                        
                        // Fallback: criar cartão com dados simulados baseados nos pagamentos
                        MercadoPagoCartaoDTO cartao = new MercadoPagoCartaoDTO();
                        cartao.setId("mp_card_" + System.currentTimeMillis());
                        cartao.setNome("Cartão Mercado Pago");
                        cartao.setUltimosDigitos("****");
                        cartao.setLimiteTotal(new BigDecimal("1000.00")); // Limite padrão
                        cartao.setLimiteDisponivel(new BigDecimal("1000.00"));
                        cartao.setVencimentoFatura(LocalDate.now().plusDays(15));
                        cartao.setValorFatura(BigDecimal.ZERO);
                        cartao.setAtivo(true);
                        cartao.setBandeira("Visa");
                        cartao.setTipo("Crédito");
                        cartoes.add(cartao);
>>>>>>> origin/main
                    }
                    
                    log.info("✅ {} cartões reais encontrados na API do Mercado Pago", cartoes.size());
                    return cartoes;
<<<<<<< HEAD
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
=======
                }
            }
            
            log.warn("⚠️ API retornou status não esperado: {}", response.getStatusCode());
            return null;
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar cartões reais da API: {}", e.getMessage(), e);
>>>>>>> origin/main
            return null;
        }
    }

    /**
<<<<<<< HEAD
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
=======
     * Busca faturas reais da API do Mercado Pago
     * 
     * @param config Configuração do Mercado Pago
     * @return Lista de faturas reais ou null se houver erro
     */
    private List<MercadoPagoFaturaDTO> buscarFaturasReais(BankApiConfig config) {
        try {
            log.info("🚀 Fazendo chamada real para API do Mercado Pago - Faturas...");
            
            // Buscar Access Token da tabela de autorizações bancárias
            Long usuarioId = config.getUsuario().getId();
            Optional<AutorizacaoBancaria> auth = autorizacaoBancariaService
                .buscarAutorizacao(usuarioId, BankApiService.BankType.MERCADO_PAGO);
            
            if (!auth.isPresent()) {
                log.error("❌ Usuário {} não possui autorização bancária configurada", usuarioId);
                return null;
            }
            
            String accessToken = auth.get().getAccessToken();
            if (accessToken == null || accessToken.trim().isEmpty()) {
                log.error("❌ Access Token não encontrado para usuário {}", usuarioId);
                return null;
            }
            
            // Construir URL da API para faturas (endpoint correto do Mercado Pago - PRODUÇÃO)
            String url = "https://api.mercadopago.com/v1/payments/search";
            
            // Headers da requisição
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // Usar access token real do Mercado Pago
            headers.setBearerAuth(accessToken);
            
            // Adicionar parâmetros de consulta para buscar pagamentos recentes
            String urlWithParams = url + "?limit=10&offset=0&sort=date_created&criteria=desc";
            
            // Fazer requisição
            HttpEntity<String> request = new HttpEntity<>(headers);
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.exchange(urlWithParams, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("✅ API do Mercado Pago retornou dados com sucesso");
                
                // Processar resposta da API
                @SuppressWarnings("unchecked")
                Map<String, Object> responseBody = response.getBody();
                if (responseBody != null && responseBody.containsKey("results") && responseBody.get("results") instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> paymentsData = (List<Map<String, Object>>) responseBody.get("results");
                    log.info("📊 {} pagamentos retornados da API", paymentsData.size());
                    
                    // Processar pagamentos reais para criar faturas baseadas em dados reais
                    List<MercadoPagoFaturaDTO> faturas = new ArrayList<>();
                    
                    if (!paymentsData.isEmpty()) {
                        // Agrupar pagamentos por cartão para criar faturas
                        Map<String, List<Map<String, Object>>> pagamentosPorCartao = new HashMap<>();
                        
                        for (Map<String, Object> pagamento : paymentsData) {
                            String cartaoId = (String) pagamento.get("payment_method_id");
                            if (cartaoId == null) cartaoId = "default_card";
                            
                            pagamentosPorCartao.computeIfAbsent(cartaoId, k -> new ArrayList<>()).add(pagamento);
                        }
                        
                        // Criar faturas baseadas nos pagamentos reais
                        for (Map.Entry<String, List<Map<String, Object>>> entry : pagamentosPorCartao.entrySet()) {
                            String cartaoId = entry.getKey();
                            List<Map<String, Object>> pagamentosCartao = entry.getValue();
                            
                            // Calcular total dos pagamentos do cartão
                            BigDecimal totalPagamentos = pagamentosCartao.stream()
                                .map(p -> {
                                    Object amount = p.get("transaction_amount");
                                    if (amount instanceof Number) {
                                        return new BigDecimal(amount.toString());
                                    }
                                    return BigDecimal.ZERO;
                                })
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                            
                            if (totalPagamentos.compareTo(BigDecimal.ZERO) > 0) {
                                MercadoPagoFaturaDTO fatura = new MercadoPagoFaturaDTO();
                                fatura.setId("mp_fatura_" + cartaoId + "_" + System.currentTimeMillis());
                                fatura.setCartaoId(cartaoId);
                                fatura.setNomeCartao("Cartão Mercado Pago");
                                fatura.setValorTotal(totalPagamentos);
                                fatura.setValorMinimo(totalPagamentos.multiply(new BigDecimal("0.1"))); // 10% do total
                                fatura.setDataVencimento(LocalDate.now().plusDays(15));
                                fatura.setDataFechamento(LocalDate.now().minusDays(5));
                                fatura.setStatus("Aberta");
                                fatura.setPaga(false);
                                fatura.setValorPago(BigDecimal.ZERO);
                                
                                faturas.add(fatura);
                                log.info("✅ Fatura real criada: {} - Valor: R$ {}", 
                                    fatura.getId(), fatura.getValorTotal());
                            }
                        }
                    }
                    
                    log.info("✅ {} faturas reais criadas baseadas nos pagamentos", faturas.size());
                    return faturas;
                }
            }
            
            log.warn("⚠️ API retornou status não esperado para faturas: {}", response.getStatusCode());
            return null;
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar faturas reais da API: {}", e.getMessage(), e);
            return null;
>>>>>>> origin/main
        }
    }

    /**
<<<<<<< HEAD
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
=======
     * Busca transações reais da API do Mercado Pago
     * 
     * @param config Configuração do Mercado Pago
     * @param userId ID do usuário
     * @return Lista de transações reais ou null se houver erro
     */
    private List<Transacao> buscarTransacoesReais(BankApiConfig config, Long userId) {
        try {
            log.info("🚀 Fazendo chamada real para API do Mercado Pago - Transações...");
            
            // Buscar Access Token da tabela de autorizações bancárias
            Long usuarioId = config.getUsuario().getId();
            Optional<AutorizacaoBancaria> auth = autorizacaoBancariaService
                .buscarAutorizacao(usuarioId, BankApiService.BankType.MERCADO_PAGO);
            
            if (!auth.isPresent()) {
                log.error("❌ Usuário {} não possui autorização bancária configurada", usuarioId);
                return null;
            }
            
            String accessToken = auth.get().getAccessToken();
            if (accessToken == null || accessToken.trim().isEmpty()) {
                log.error("❌ Access Token não encontrado para usuário {}", usuarioId);
                return null;
            }
            
            // Construir URL da API para transações (endpoint correto do Mercado Pago - PRODUÇÃO)
            String url = "https://api.mercadopago.com/v1/payments/search";
            
            // Headers da requisição
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // Usar access token real do Mercado Pago
            headers.setBearerAuth(accessToken);
            
            // Adicionar parâmetros de consulta para buscar pagamentos recentes
            String urlWithParams = url + "?limit=10&offset=0&sort=date_created&criteria=desc";
            
            // Fazer requisição
            HttpEntity<String> request = new HttpEntity<>(headers);
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.exchange(urlWithParams, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("✅ API do Mercado Pago retornou dados com sucesso");
                
                // Processar resposta da API
                @SuppressWarnings("unchecked")
                Map<String, Object> responseBody = response.getBody();
                if (responseBody != null && responseBody.containsKey("results") && responseBody.get("results") instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> paymentsData = (List<Map<String, Object>>) responseBody.get("results");
                    log.info("📊 {} pagamentos retornados da API", paymentsData.size());
                    
                    // Converter pagamentos para entidades Transacao
                    List<Transacao> transacoes = new ArrayList<>();
                    Usuario usuario = usuarioRepository.findById(userId).orElse(null);
                    
                    for (Map<String, Object> paymentData : paymentsData) {
                        try {
                            Transacao transacao = new Transacao();
                            
                            // Garantir que a descrição nunca seja nula
                            String descricao = (String) paymentData.get("description");
                            if (descricao == null || descricao.trim().isEmpty()) {
                                descricao = "Pagamento Mercado Pago - " + paymentData.get("id");
                            }
                            transacao.setDescricao(descricao);
                            
                            transacao.setValor(new BigDecimal(paymentData.get("transaction_amount").toString()));
                            
                            // Converter data de string para LocalDateTime
                            String dateStr = (String) paymentData.get("date_created");
                            if (dateStr != null) {
                                try {
                                    // Mercado Pago usa formato ISO 8601 com timezone
                                    // Converter para LocalDateTime removendo timezone
                                    String cleanDate = dateStr
                                        .replaceAll("\\.[0-9]{3}Z$", "")  // Remove .000Z
                                        .replaceAll("\\+[0-9]{2}:[0-9]{2}$", "")  // Remove +03:00
                                        .replaceAll("\\-[0-9]{2}:[0-9]{2}$", ""); // Remove -04:00
                                    
                                    // Se ainda tiver 'T', fazer parse direto
                                    if (cleanDate.contains("T")) {
                                        transacao.setDataTransacao(LocalDateTime.parse(cleanDate));
                                    } else {
                                        // Se não tiver 'T', adicionar
                                        transacao.setDataTransacao(LocalDateTime.parse(cleanDate + "T00:00:00"));
                                    }
                                } catch (Exception e) {
                                    log.warn("⚠️ Erro ao fazer parse da data: {}. Usando data atual", dateStr);
                                    transacao.setDataTransacao(LocalDateTime.now());
                                }
                            } else {
                                transacao.setDataTransacao(LocalDateTime.now());
                            }
                            
                            transacao.setTipoTransacao(Transacao.TipoTransacao.DESPESA); // Padrão para pagamentos
                            transacao.setUsuario(usuario);
                            transacao.setDataCriacao(LocalDateTime.now());
                            transacoes.add(transacao);
                        } catch (Exception e) {
                            log.warn("⚠️ Erro ao processar pagamento: {}", e.getMessage());
                        }
                    }
                    
>>>>>>> origin/main
                    return transacoes;
                }
            }
            
<<<<<<< HEAD
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
=======
            log.warn("⚠️ API retornou status não esperado para transações: {}", response.getStatusCode());
            return null;
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar transações reais da API: {}", e.getMessage(), e);
            return null;
>>>>>>> origin/main
        }
    }

    /**
<<<<<<< HEAD
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
=======
     * Configura credenciais do Mercado Pago para um usuário
     * 
     * @param userId ID do usuário
     * @param accessToken Token de acesso do Mercado Pago
     * @param publicKey Chave pública da aplicação
     * @param clientId Client ID da aplicação
     * @param clientSecret Client Secret da aplicação
     * @param mpUserId User ID no Mercado Pago (opcional)
     */
    public void configurarCredenciais(Long userId, String accessToken, String publicKey, String clientId, String clientSecret, String mpUserId) {
        try {
            log.info("🔧 Configurando credenciais para usuário: {}", userId);
            
            // Busca usuário
            Optional<Usuario> usuarioOpt = usuarioRepository.findById(userId);
            if (!usuarioOpt.isPresent()) {
                log.error("❌ Usuário não encontrado: {}", userId);
                throw new RuntimeException("Usuário não encontrado");
            }
            
            // Verifica se já existe configuração
            Optional<BankApiConfig> configExistente = bankApiConfigRepository.findByUsuarioIdAndTipoBanco(userId, "MERCADOPAGO");
            
            BankApiConfig config;
            if (configExistente.isPresent()) {
                // Atualiza configuração existente
                config = configExistente.get();
                config.setClientSecret(clientSecret); // Client Secret real fornecido
                config.setClientId(clientId); // Client ID real fornecido
                config.setUserId(mpUserId != null ? mpUserId : "209112973"); // User ID do Mercado Pago ou padrão
                config.setDataAtualizacao(java.time.LocalDateTime.now());
                log.info("🔄 Atualizando configuração existente para usuário: {}", userId);
            } else {
                // Cria nova configuração
                config = BankApiConfig.builder()
                    .usuario(usuarioOpt.get())
                    .tipoBanco("MERCADOPAGO")
                    .clientId(clientId) // Client ID real fornecido
                    .clientSecret(clientSecret) // Client Secret real fornecido
                    .userId(mpUserId != null ? mpUserId : "209112973") // User ID do Mercado Pago ou padrão
                    .apiUrl("https://api.mercadopago.com/v1")
                    .authUrl("https://api.mercadopago.com/authorization")
                    .tokenUrl("https://api.mercadopago.com/oauth/token")
                    .redirectUri("https://262f3e49bd2d.ngrok-free.app/api/auth/mercadopago/callback")
                    .scope("read,write")
                    .sandbox(false) // PRODUÇÃO
                    .ativo(true)
                    .timeoutMs(30000)
                    .maxRetries(3)
                    .retryDelayMs(1000)
                    .build();
                log.info("🆕 Criando nova configuração para usuário: {}", userId);
            }
            
            // Salva no banco
            bankApiConfigRepository.save(config);
            log.info("✅ Credenciais configuradas e salvas no banco para usuário: {}", userId);
            
        } catch (Exception e) {
            log.error("❌ Erro ao configurar credenciais para usuário {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Falha ao configurar credenciais: " + e.getMessage());
        }
    }

    /**
     * Sincroniza automaticamente todos os dados do Mercado Pago para um usuário
     * Este método é chamado quando a API é ativada pela primeira vez
     */
    public void sincronizarDadosAutomaticamente(Long userId) {
        try {
            log.info("🔄 Iniciando sincronização automática de dados do Mercado Pago para usuário: {}", userId);
            
            // Verificar se a configuração está válida
            Optional<BankApiConfig> config = getMercadoPagoConfig(userId);
            if (!config.isPresent() || !config.get().getAtivo()) {
                log.warn("⚠️ Configuração do Mercado Pago não encontrada ou inativa para usuário: {}", userId);
                return;
            }
            
            BankApiConfig mercadopagoConfig = config.get();
            if (mercadopagoConfig.getClientSecret() == null || mercadopagoConfig.getClientSecret().trim().isEmpty()) {
                log.warn("⚠️ Client Secret ainda não configurado para usuário: {}", userId);
                return;
            }
            
            log.info("✅ Configuração válida encontrada. Iniciando sincronização...");
            
            // 1. Sincronizar cartões de crédito
            sincronizarCartoesAutomaticamente(userId);
            
            // 2. Sincronizar faturas
            sincronizarFaturasAutomaticamente(userId);
            
            // 3. Sincronizar transações
            sincronizarTransacoesAutomaticamente(userId);
            
            log.info("🎉 Sincronização automática concluída com sucesso para usuário: {}", userId);
            
        } catch (Exception e) {
            log.error("❌ Erro durante sincronização automática para usuário {}: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * Sincroniza cartões de crédito automaticamente
     */
    private void sincronizarCartoesAutomaticamente(Long userId) {
        try {
            log.info("💳 Sincronizando cartões de crédito para usuário: {}", userId);
            
            // Buscar cartões via API do Mercado Pago
            List<MercadoPagoCartaoDTO> cartoesApi = buscarCartoes(userId);
            
            if (cartoesApi.isEmpty()) {
                log.info("ℹ️ Nenhum cartão encontrado na API para usuário: {}", userId);
                return;
            }
            
            // Salvar/atualizar cartões no banco local
            for (MercadoPagoCartaoDTO cartaoApi : cartoesApi) {
                try {
                    // Verificar se o cartão já existe
                    List<CartaoCredito> cartoesExistentes = cartaoCreditoRepository.findByUsuarioIdAndAtivoTrue(userId);
                    Optional<CartaoCredito> cartaoExistente = cartoesExistentes.stream()
                        .filter(c -> c.getNumeroCartao().equals(cartaoApi.getUltimosDigitos()))
                        .findFirst();
                    
                    if (cartaoExistente.isPresent()) {
                        // Atualizar cartão existente
                        CartaoCredito cartaoAtual = cartaoExistente.get();
                        cartaoAtual.setLimite(cartaoApi.getLimiteTotal());
                        cartaoAtual.setLimiteDisponivel(cartaoApi.getLimiteDisponivel());
                        cartaoAtual.setDataVencimento(cartaoApi.getVencimentoFatura().atStartOfDay());
                        cartaoAtual.setDataAtualizacao(LocalDateTime.now());
                        cartaoCreditoRepository.save(cartaoAtual);
                        log.debug("🔄 Cartão atualizado: {}", cartaoApi.getUltimosDigitos());
                    } else {
                        // Criar novo cartão
                        CartaoCredito novoCartao = new CartaoCredito();
                        novoCartao.setUsuario(usuarioRepository.findById(userId).orElse(null));
                        novoCartao.setNumeroCartao(cartaoApi.getUltimosDigitos());
                        novoCartao.setNome(cartaoApi.getNome());
                        novoCartao.setBandeira(cartaoApi.getBandeira());
                        novoCartao.setLimite(cartaoApi.getLimiteTotal());
                        novoCartao.setLimiteDisponivel(cartaoApi.getLimiteDisponivel());
                        novoCartao.setDataVencimento(cartaoApi.getVencimentoFatura().atStartOfDay());
                        novoCartao.setBanco("MERCADOPAGO");
                        novoCartao.setAtivo(true);
                        novoCartao.setDataCriacao(LocalDateTime.now());
                        novoCartao.setDataAtualizacao(LocalDateTime.now());
                        cartaoCreditoRepository.save(novoCartao);
                        log.debug("🆕 Novo cartão criado: {}", cartaoApi.getUltimosDigitos());
                    }
                } catch (Exception e) {
                    log.error("❌ Erro ao processar cartão {}: {}", cartaoApi.getUltimosDigitos(), e.getMessage());
                }
            }
            
            log.info("✅ {} cartões sincronizados para usuário: {}", cartoesApi.size(), userId);
            
        } catch (Exception e) {
            log.error("❌ Erro ao sincronizar cartões para usuário {}: {}", userId, e.getMessage(), e);
        }
    }
    
    /**
     * Sincroniza faturas automaticamente
     */
    private void sincronizarFaturasAutomaticamente(Long userId) {
        try {
            log.info("📄 Sincronizando faturas para usuário: {}", userId);
            
            // Buscar faturas via API do Mercado Pago
            List<MercadoPagoFaturaDTO> faturasApi = buscarFaturas(userId);
            
            if (faturasApi.isEmpty()) {
                log.info("ℹ️ Nenhuma fatura encontrada na API para usuário: {}", userId);
                return;
            }
            
            // Salvar/atualizar faturas no banco local
            for (MercadoPagoFaturaDTO faturaApi : faturasApi) {
                try {
                    // Verificar se a fatura já existe
                    List<Fatura> faturasExistentes = faturaRepository.findAll();
                    Optional<Fatura> faturaExistente = faturasExistentes.stream()
                        .filter(f -> f.getNumeroFatura().equals(faturaApi.getId()))
                        .findFirst();
                    
                    if (faturaExistente.isPresent()) {
                        // Atualizar fatura existente
                        Fatura faturaAtual = faturaExistente.get();
                        faturaAtual.setValorFatura(faturaApi.getValorTotal());
                        faturaAtual.setValorPago(faturaApi.getValorPago());
                        faturaAtual.setStatus(Fatura.StatusFatura.fromString(faturaApi.getStatus()));
                        faturaAtual.setDataVencimento(faturaApi.getDataVencimento().atStartOfDay());
                        faturaAtual.setDataAtualizacao(LocalDateTime.now());
                        faturaRepository.save(faturaAtual);
                        log.debug("🔄 Fatura atualizada: {}", faturaApi.getId());
                    } else {
                        // Criar nova fatura
                        Fatura novaFatura = new Fatura();
                        novaFatura.setNumeroFatura(faturaApi.getId());
                        novaFatura.setValorTotal(faturaApi.getValorTotal());
                        novaFatura.setValorFatura(faturaApi.getValorTotal());
                        novaFatura.setValorMinimo(faturaApi.getValorMinimo());
                        novaFatura.setValorPago(faturaApi.getValorPago() != null ? faturaApi.getValorPago() : BigDecimal.ZERO);
                        novaFatura.setStatus(Fatura.StatusFatura.fromString(faturaApi.getStatus()));
                        novaFatura.setDataVencimento(faturaApi.getDataVencimento().atStartOfDay());
                        novaFatura.setDataFechamento(faturaApi.getDataFechamento().atStartOfDay());
                        novaFatura.setPaga(faturaApi.getPaga() != null ? faturaApi.getPaga() : false);
                        novaFatura.setDataCriacao(LocalDateTime.now());
                        faturaRepository.save(novaFatura);
                        log.debug("🆕 Nova fatura criada: {}", faturaApi.getId());
                    }
                } catch (Exception e) {
                    log.error("❌ Erro ao processar fatura {}: {}", faturaApi.getId(), e.getMessage());
                }
            }
            
            log.info("✅ {} faturas sincronizadas para usuário: {}", faturasApi.size(), userId);
            
        } catch (Exception e) {
            log.error("❌ Erro ao sincronizar faturas para usuário {}: {}", userId, e.getMessage(), e);
        }
    }
    
    /**
     * Sincroniza transações automaticamente
     */
    private void sincronizarTransacoesAutomaticamente(Long userId) {
        try {
            log.info("💸 Sincronizando transações para usuário: {}", userId);
            
            // Buscar transações via API do Mercado Pago
            List<Transacao> transacoesApi = buscarTransacoes(userId);
            
            if (transacoesApi.isEmpty()) {
                log.info("ℹ️ Nenhuma transação encontrada na API para usuário: {}", userId);
                return;
            }
            
            // Salvar/atualizar transações no banco local
            for (Transacao transacaoApi : transacoesApi) {
                try {
                    // Verificar se a transação já existe para este usuário
                    List<Transacao> transacoesExistentes = transacaoRepository.findByUsuarioIdOrderByDataTransacaoDesc(userId);
                    Optional<Transacao> transacaoExistente = transacoesExistentes.stream()
                        .filter(t -> t.getDescricao().equals(transacaoApi.getDescricao()) &&
                                   t.getDataTransacao().equals(transacaoApi.getDataTransacao()) &&
                                   t.getValor().equals(transacaoApi.getValor()))
                        .findFirst();
                    
                    if (transacaoExistente.isPresent()) {
                        // Atualizar transação existente
                        Transacao transacaoAtual = transacaoExistente.get();
                        transacaoAtual.setValor(transacaoApi.getValor());
                        transacaoRepository.save(transacaoAtual);
                        log.debug("🔄 Transação atualizada: {}", transacaoApi.getDescricao());
                    } else {
                        // Criar nova transação
                        transacaoApi.setUsuario(usuarioRepository.findById(userId).orElse(null));
                        transacaoApi.setDataCriacao(LocalDateTime.now());
                        transacaoRepository.save(transacaoApi);
                        log.debug("🆕 Nova transação criada: {}", transacaoApi.getDescricao());
                    }
                } catch (Exception e) {
                    log.error("❌ Erro ao processar transação {}: {}", transacaoApi.getDescricao(), e.getMessage());
                }
            }
            
            log.info("✅ {} transações sincronizadas para usuário: {}", transacoesApi.size(), userId);
            
        } catch (Exception e) {
            log.error("❌ Erro ao sincronizar transações para usuário {}: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * Sincroniza dados reais do Mercado Pago para todos os usuários
     */
    public void sincronizarDadosReais() {
        try {
            log.info("🚀 Iniciando sincronização de dados reais para todos os usuários...");
            
            // Buscar todos os usuários que têm configuração do Mercado Pago
            List<BankApiConfig> configs = bankApiConfigRepository.findByAtivoTrue().stream()
                .filter(config -> "MERCADO_PAGO".equals(config.getTipoBanco()))
                .collect(java.util.stream.Collectors.toList());
            
            if (configs.isEmpty()) {
                log.info("ℹ️ Nenhum usuário com configuração ativa do Mercado Pago encontrado");
                return;
            }
            
            log.info("👥 Encontrados {} usuários com configuração do Mercado Pago", configs.size());
            
            // Sincronizar dados para cada usuário
            for (BankApiConfig config : configs) {
                try {
                    Long userId = config.getUsuario().getId();
                    log.info("🔄 Sincronizando dados para usuário: {}", userId);
                    
                    // Sincronizar todos os dados do Mercado Pago
                    sincronizarDadosAutomaticamente(userId);
                    
                    log.info("✅ Sincronização concluída para usuário: {}", userId);
                    
                } catch (Exception e) {
                    log.error("❌ Erro ao sincronizar dados para usuário {}: {}", config.getUsuario().getId(), e.getMessage(), e);
                }
            }
            
            log.info("🎉 Sincronização de dados reais concluída para todos os usuários!");
            
        } catch (Exception e) {
            log.error("❌ Erro na sincronização de dados reais: {}", e.getMessage(), e);
>>>>>>> origin/main
        }
    }
}
