package com.consumoesperto.service;

import com.consumoesperto.model.AutorizacaoBancaria;
import com.consumoesperto.service.AutorizacaoBancariaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Serviço responsável pela integração com APIs de bancos e instituições financeiras
 * 
 * Este serviço implementa a integração OAuth2 com múltiplas instituições financeiras
 * para permitir que usuários conectem suas contas bancárias e cartões de crédito.
 * Oferece funcionalidades de autenticação, obtenção de dados financeiros e
 * gerenciamento de tokens de acesso.
 * 
 * Bancos suportados:
 * - Nubank
 * - Mercado Pago
 * - Itaú
 * - Inter
 * 
 * Funcionalidades principais:
 * - Fluxo OAuth2 para autorização com bancos
 * - Troca de código de autorização por token de acesso
 * - Consulta de saldo de contas
 * - Obtenção de transações bancárias
 * - Consulta de cartões de crédito
 * - Obtenção de faturas de cartão
 * - Renovação automática de tokens
 * - Gerenciamento de credenciais por banco
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Service
@RequiredArgsConstructor // Lombok: gera construtor com campos final automaticamente
@Slf4j // Lombok: fornece logger automático para a classe
public class BankApiService {

    // Cliente WebClient para requisições HTTP reativas
    private final WebClient webClient;
    
    // RestTemplate para requisições HTTP síncronas (OAuth2)
    private final RestTemplate restTemplate;
    
    // Serviço para gerenciar autorizações bancárias
    private final AutorizacaoBancariaService autorizacaoBancariaService;

    // Configurações do Nubank
    @Value("${bank.api.nubank.client-id}")
    private String nubankClientId;

    @Value("${bank.api.nubank.client-secret}")
    private String nubankClientSecret;

    @Value("${bank.api.nubank.auth-url}")
    private String nubankAuthUrl;

    @Value("${bank.api.nubank.token-url}")
    private String nubankTokenUrl;

    @Value("${bank.api.nubank.api-url}")
    private String nubankApiUrl;

    // Configurações do Mercado Pago
    @Value("${bank.api.mercadopago.client-id}")
    private String mercadoPagoClientId;

    @Value("${bank.api.mercadopago.client-secret}")
    private String mercadoPagoClientSecret;

    @Value("${bank.api.mercadopago.auth-url}")
    private String mercadoPagoAuthUrl;

    @Value("${bank.api.mercadopago.token-url}")
    private String mercadoPagoTokenUrl;

    @Value("${bank.api.mercadopago.api-url}")
    private String mercadoPagoApiUrl;

    // Configurações do Itaú
    @Value("${bank.api.itau.client-id}")
    private String itauClientId;

    @Value("${bank.api.itau.client-secret}")
    private String itauClientSecret;

    @Value("${bank.api.itau.auth-url}")
    private String itauAuthUrl;

    @Value("${bank.api.itau.token-url}")
    private String itauTokenUrl;

    @Value("${bank.api.itau.api-url}")
    private String itauApiUrl;

    // Configurações do Inter
    @Value("${bank.api.inter.client-id}")
    private String interClientId;

    @Value("${bank.api.inter.client-secret}")
    private String interClientSecret;

    @Value("${bank.api.inter.auth-url}")
    private String interAuthUrl;

    @Value("${bank.api.inter.token-url}")
    private String interTokenUrl;

    @Value("${bank.api.inter.api-url}")
    private String interApiUrl;

    /**
     * Enumeração dos tipos de banco suportados pela aplicação
     * 
     * Cada banco possui suas próprias configurações de API e endpoints
     * específicos para autenticação e consulta de dados.
     */
    public enum BankType {
        NUBANK,        // Nubank - banco digital
        ITAU,          // Itaú - banco tradicional
        MERCADO_PAGO,  // Mercado Pago - fintech
        INTER          // Inter - banco digital
    }

    /**
     * Gera URL de autorização OAuth2 para um banco específico
     * 
     * Este método constrói a URL de autorização OAuth2 que o usuário
     * deve acessar para autorizar o acesso aos dados bancários.
     * 
     * Parâmetros da URL:
     * - client_id: identificador da aplicação no banco
     * - redirect_uri: URL de retorno após autorização
     * - response_type: tipo de resposta (code para OAuth2)
     * - scope: permissões solicitadas (read para leitura)
     * - state: valor para prevenir ataques CSRF
     * 
     * @param bankType Tipo do banco para gerar a URL
     * @param redirectUri URL de retorno após autorização
     * @param state Valor de estado para segurança
     * @return URL completa de autorização OAuth2
     */
    public String getAuthorizationUrl(BankType bankType, String redirectUri, String state) {
        String authUrl = "";
        String clientId = "";

        // Seleciona configurações específicas do banco
        switch (bankType) {
            case NUBANK:
                authUrl = nubankAuthUrl;
                clientId = nubankClientId;
                break;
            case MERCADO_PAGO:
                authUrl = mercadoPagoAuthUrl;
                clientId = mercadoPagoClientId;
                break;
            case ITAU:
                authUrl = itauAuthUrl;
                clientId = itauClientId;
                break;
            case INTER:
                authUrl = interAuthUrl;
                clientId = interClientId;
                break;
        }

        // Constrói URL de autorização com todos os parâmetros necessários
        return String.format("%s?client_id=%s&redirect_uri=%s&response_type=code&scope=read&state=%s",
                authUrl, clientId, redirectUri, state);
    }

    /**
     * Troca código de autorização por token de acesso OAuth2
     * 
     * Este método implementa o segundo passo do fluxo OAuth2, onde
     * o código de autorização recebido é trocado por um token de acesso
     * que permite consultar os dados bancários do usuário.
     * 
     * Processo de troca:
     * 1. Seleciona configurações específicas do banco
     * 2. Configura headers de autenticação (Basic Auth)
     * 3. Monta corpo da requisição com código e redirect_uri
     * 4. Envia requisição POST para o endpoint de token
     * 5. Retorna resposta com token de acesso e refresh token
     * 
     * @param bankType Tipo do banco para troca do token
     * @param authorizationCode Código de autorização recebido do banco
     * @param redirectUri URL de redirecionamento (deve ser igual à usada na autorização)
     * @return Map com token de acesso, refresh token e outras informações
     */
    public Map<String, Object> exchangeCodeForToken(BankType bankType, String authorizationCode, String redirectUri) {
        String tokenUrl = "";
        String clientId = "";
        String clientSecret = "";

        // Seleciona configurações específicas do banco
        switch (bankType) {
            case NUBANK:
                tokenUrl = nubankTokenUrl;
                clientId = nubankClientId;
                clientSecret = nubankClientSecret;
                break;
            case MERCADO_PAGO:
                tokenUrl = mercadoPagoTokenUrl;
                clientId = mercadoPagoClientId;
                clientSecret = mercadoPagoClientSecret;
                break;
            case ITAU:
                tokenUrl = itauTokenUrl;
                clientId = itauClientId;
                clientSecret = itauClientSecret;
                break;
            case INTER:
                tokenUrl = interTokenUrl;
                clientId = interClientId;
                clientSecret = interClientSecret;
                break;
        }

        // Configura headers de autenticação e tipo de conteúdo
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(clientId, clientSecret);
        headers.set("Content-Type", "application/x-www-form-urlencoded");

        // Monta corpo da requisição OAuth2
        String body = String.format("grant_type=authorization_code&code=%s&redirect_uri=%s",
                authorizationCode, redirectUri);

        // Cria entidade HTTP com headers e corpo
        HttpEntity<String> request = new HttpEntity<>(body, headers);

        // Envia requisição POST para trocar código por token
        ResponseEntity<Map> response = restTemplate.exchange(tokenUrl, HttpMethod.POST, request, Map.class);
        return response.getBody();
    }

    /**
     * Consulta saldo da conta bancária do usuário
     * 
     * Este método utiliza o token de acesso para consultar o saldo
     * atual da conta bancária conectada.
     * 
     * @param bankType Tipo do banco para consulta
     * @param accessToken Token de acesso OAuth2 válido
     * @return Map com informações de saldo da conta
     */
    public Map<String, Object> getAccountBalance(BankType bankType, String accessToken) {
        // Constrói URL do endpoint de saldo
        String apiUrl = getApiUrl(bankType) + "/accounts/balance";
        
        // Faz requisição GET autenticada usando WebClient
        return webClient.get()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    /**
     * Consulta transações da conta bancária do usuário
     * 
     * Este método utiliza o token de acesso para consultar o histórico
     * de transações de uma conta específica.
     * 
     * @param bankType Tipo do banco para consulta
     * @param accessToken Token de acesso OAuth2 válido
     * @param accountId ID da conta para consulta de transações
     * @return Map com lista de transações da conta
     */
    public Map<String, Object> getTransactions(BankType bankType, String accessToken, String accountId) {
        // Constrói URL do endpoint de transações
        String apiUrl = getApiUrl(bankType) + "/accounts/" + accountId + "/transactions";
        
        // Faz requisição GET autenticada usando WebClient
        return webClient.get()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    /**
     * Consulta cartões de crédito do usuário
     * 
     * Este método utiliza o token de acesso para consultar os
     * cartões de crédito disponíveis na conta bancária.
     * 
     * @param bankType Tipo do banco para consulta
     * @param accessToken Token de acesso OAuth2 válido
     * @return Map com lista de cartões de crédito
     */
    public Map<String, Object> getCreditCards(BankType bankType, String accessToken) {
        // Constrói URL do endpoint de cartões de crédito
        String apiUrl = getApiUrl(bankType) + "/credit-cards";
        
        // Faz requisição GET autenticada usando WebClient
        return webClient.get()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    /**
     * Consulta faturas de um cartão de crédito específico
     * 
     * Este método utiliza o token de acesso para consultar as
     * faturas de um cartão de crédito específico.
     * 
     * @param bankType Tipo do banco para consulta
     * @param accessToken Token de acesso OAuth2 válido
     * @param cardId ID do cartão de crédito para consulta
     * @return Map com lista de faturas do cartão
     */
    public Map<String, Object> getCreditCardInvoices(BankType bankType, String accessToken, String cardId) {
        // Constrói URL do endpoint de faturas do cartão
        String apiUrl = getApiUrl(bankType) + "/credit-cards/" + cardId + "/invoices";
        
        // Faz requisição GET autenticada usando WebClient
        return webClient.get()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    /**
     * Obtém URL base da API para um banco específico
     * 
     * Método utilitário que retorna a URL base da API
     * correspondente ao tipo de banco especificado.
     * 
     * @param bankType Tipo do banco para obter a URL da API
     * @return String com URL base da API do banco
     * @throws IllegalArgumentException se o banco não for suportado
     */
    private String getApiUrl(BankType bankType) {
        switch (bankType) {
            case NUBANK:
                return nubankApiUrl;
            case MERCADO_PAGO:
                return mercadoPagoApiUrl;
            case ITAU:
                return itauApiUrl;
            case INTER:
                return interApiUrl;
            default:
                throw new IllegalArgumentException("Banco não suportado: " + bankType);
        }
    }

    /**
     * Renova token de acesso usando refresh token
     * 
     * Este método implementa a renovação automática de tokens OAuth2
     * quando o token de acesso expira, usando o refresh token.
     * 
     * Processo de renovação:
     * 1. Seleciona configurações específicas do banco
     * 2. Configura headers de autenticação (Basic Auth)
     * 3. Monta corpo da requisição com refresh token
     * 4. Envia requisição POST para renovar token
     * 5. Retorna true se renovação for bem-sucedida
     * 
     * @param bankType Tipo do banco para renovação do token
     * @param refreshToken Refresh token para renovar o acesso
     * @return true se a renovação for bem-sucedida, false caso contrário
     */
    public boolean refreshTokenIfNeeded(BankType bankType, String refreshToken) {
        try {
            String tokenUrl = "";
            String clientId = "";
            String clientSecret = "";

            // Seleciona configurações específicas do banco
            switch (bankType) {
                case NUBANK:
                    tokenUrl = nubankTokenUrl;
                    clientId = nubankClientId;
                    clientSecret = nubankClientSecret;
                    break;
                case MERCADO_PAGO:
                    tokenUrl = mercadoPagoTokenUrl;
                    clientId = mercadoPagoClientId;
                    clientSecret = mercadoPagoClientSecret;
                    break;
                case ITAU:
                    tokenUrl = itauTokenUrl;
                    clientId = itauClientId;
                    clientSecret = itauClientSecret;
                    break;
                case INTER:
                    tokenUrl = interTokenUrl;
                    clientId = interClientId;
                    clientSecret = interClientSecret;
                    break;
            }

            // Configura headers de autenticação e tipo de conteúdo
            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(clientId, clientSecret);
            headers.set("Content-Type", "application/x-www-form-urlencoded");

            // Monta corpo da requisição para renovação de token
            String body = String.format("grant_type=refresh_token&refresh_token=%s", refreshToken);

            // Cria entidade HTTP com headers e corpo
            HttpEntity<String> request = new HttpEntity<>(body, headers);

            // Envia requisição POST para renovar token
            ResponseEntity<Map> response = restTemplate.exchange(tokenUrl, HttpMethod.POST, request, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            // Log do erro para debugging e auditoria
            log.error("Erro ao renovar token para banco: " + bankType, e);
            return false;
        }
    }

    // ===== MÉTODOS PARA BUSCAR DADOS REAIS DOS BANCOS =====

    /**
     * Busca saldo real da conta de um usuário em um banco específico
     * 
     * @param userId ID do usuário
     * @param bankType Tipo do banco
     * @return Map com dados do saldo ou null se não houver autorização
     */
    public Map<String, Object> getRealAccountBalance(Long userId, BankType bankType) {
        try {
            // Busca autorização ativa do usuário para o banco
            Optional<AutorizacaoBancaria> autorizacao = autorizacaoBancariaService.buscarAutorizacao(userId, bankType);
            
            if (autorizacao.isEmpty()) {
                log.warn("Usuário {} não possui autorização ativa para banco {}", userId, bankType);
                return null;
            }

            AutorizacaoBancaria auth = autorizacao.get();
            
            // Verifica se o token está expirado
            if (auth.getDataExpiracao().isBefore(LocalDateTime.now())) {
                log.info("Token expirado para usuário {} e banco {}, tentando renovar", userId, bankType);
                if (!renovarTokenExpirado(auth)) {
                    log.error("Falha ao renovar token para usuário {} e banco {}", userId, bankType);
                    return null;
                }
            }

            // Busca saldo real do banco
            Map<String, Object> saldo = getAccountBalance(bankType, auth.getAccessToken());
            
            // Marca autorização como utilizada
            autorizacaoBancariaService.marcarComoUtilizada(auth.getId());
            
            return saldo;
            
        } catch (Exception e) {
            log.error("Erro ao buscar saldo real para usuário {} e banco {}", userId, bankType, e);
            return null;
        }
    }

    /**
     * Busca cartões de crédito reais de um usuário em um banco específico
     * 
     * @param userId ID do usuário
     * @param bankType Tipo do banco
     * @return Map com lista de cartões ou null se não houver autorização
     */
    public Map<String, Object> getRealCreditCards(Long userId, BankType bankType) {
        try {
            // Busca autorização ativa do usuário para o banco
            Optional<AutorizacaoBancaria> autorizacao = autorizacaoBancariaService.buscarAutorizacao(userId, bankType);
            
            if (autorizacao.isEmpty()) {
                log.warn("Usuário {} não possui autorização ativa para banco {}", userId, bankType);
                return null;
            }
            
            AutorizacaoBancaria auth = autorizacao.get();
            
            // Verifica se o token está expirado
            if (auth.getDataExpiracao().isBefore(LocalDateTime.now())) {
                log.info("Token expirado para usuário {} e banco {}, tentando renovar", userId, bankType);
                if (!renovarTokenExpirado(auth)) {
                    log.error("Falha ao renovar token para usuário {} e banco {}", userId, bankType);
                    return null;
                }
            }

            // Busca cartões reais do banco
            Map<String, Object> cartoes = getCreditCards(bankType, auth.getAccessToken());
            
            // Marca autorização como utilizada
            autorizacaoBancariaService.marcarComoUtilizada(auth.getId());
            
            return cartoes;
            
        } catch (Exception e) {
            log.error("Erro ao buscar cartões reais para usuário {} e banco {}", userId, bankType, e);
            return null;
        }
    }

    /**
     * Busca faturas reais de um cartão de crédito
     * 
     * @param userId ID do usuário
     * @param bankType Tipo do banco
     * @param cardId ID do cartão
     * @return Map com lista de faturas ou null se não houver autorização
     */
    public Map<String, Object> getRealCreditCardInvoices(Long userId, BankType bankType, String cardId) {
        try {
            // Busca autorização ativa do usuário para o banco
            Optional<AutorizacaoBancaria> autorizacao = autorizacaoBancariaService.buscarAutorizacao(userId, bankType);
            
            if (autorizacao.isEmpty()) {
                log.warn("Usuário {} não possui autorização ativa para banco {}", userId, bankType);
                return null;
            }
            
            AutorizacaoBancaria auth = autorizacao.get();
            
            // Verifica se o token está expirado
            if (auth.getDataExpiracao().isBefore(LocalDateTime.now())) {
                log.info("Token expirado para usuário {} e banco {}, tentando renovar", userId, bankType);
                if (!renovarTokenExpirado(auth)) {
                    log.error("Falha ao renovar token para usuário {} e banco {}", userId, bankType);
                    return null;
                }
            }

            // Busca faturas reais do banco
            Map<String, Object> faturas = getCreditCardInvoices(bankType, auth.getAccessToken(), cardId);
            
            // Marca autorização como utilizada
            autorizacaoBancariaService.marcarComoUtilizada(auth.getId());
            
            return faturas;
            
        } catch (Exception e) {
            log.error("Erro ao buscar faturas reais para usuário {} e banco {} e cartão {}", userId, bankType, cardId, e);
            return null;
        }
    }

    /**
     * Busca transações reais de uma conta
     * 
     * @param userId ID do usuário
     * @param bankType Tipo do banco
     * @param accountId ID da conta
     * @return Map com lista de transações ou null se não houver autorização
     */
    public Map<String, Object> getRealTransactions(Long userId, BankType bankType, String accountId) {
        try {
            // Busca autorização ativa do usuário para o banco
            Optional<AutorizacaoBancaria> autorizacao = autorizacaoBancariaService.buscarAutorizacao(userId, bankType);
            
            if (autorizacao.isEmpty()) {
                log.warn("Usuário {} não possui autorização ativa para banco {}", userId, bankType);
                return null;
            }
            
            AutorizacaoBancaria auth = autorizacao.get();
            
            // Verifica se o token está expirado
            if (auth.getDataExpiracao().isBefore(LocalDateTime.now())) {
                log.info("Token expirado para usuário {} e banco {}, tentando renovar", userId, bankType);
                if (!renovarTokenExpirado(auth)) {
                    log.error("Falha ao renovar token para usuário {} e banco {}", userId, bankType);
                    return null;
                }
            }

            // Busca transações reais do banco
            Map<String, Object> transacoes = getTransactions(bankType, auth.getAccessToken(), accountId);
            
            // Marca autorização como utilizada
            autorizacaoBancariaService.marcarComoUtilizada(auth.getId());
            
            return transacoes;
            
        } catch (Exception e) {
            log.error("Erro ao buscar transações reais para usuário {} e banco {} e conta {}", userId, bankType, accountId, e);
            return null;
        }
    }

    /**
     * Busca dados consolidados de todos os bancos de um usuário
     * 
     * @param userId ID do usuário
     * @return Map com dados consolidados de todos os bancos
     */
    public Map<String, Object> getConsolidatedBankData(Long userId) {
        Map<String, Object> dadosConsolidados = new HashMap<>();
        
        try {
            // Busca todas as autorizações ativas do usuário
            List<AutorizacaoBancaria> autorizacoes = autorizacaoBancariaService.buscarAutorizacoesAtivas(userId);
            
            for (AutorizacaoBancaria autorizacao : autorizacoes) {
                BankType bankType = mapTipoBanco(autorizacao.getBanco());
                
                try {
                    // Busca dados do banco
                    Map<String, Object> dadosBanco = new HashMap<>();
                    
                    // Saldo da conta
                    Map<String, Object> saldo = getRealAccountBalance(userId, bankType);
                    if (saldo != null) {
                        dadosBanco.put("saldo", saldo);
                    }
                    
                    // Cartões de crédito
                    Map<String, Object> cartoes = getRealCreditCards(userId, bankType);
                    if (cartoes != null) {
                        dadosBanco.put("cartões", cartoes);
                    }
                    
                    dadosConsolidados.put(bankType.name().toLowerCase(), dadosBanco);
                    
                } catch (Exception e) {
                    log.error("Erro ao buscar dados do banco {} para usuário {}", bankType, userId, e);
                    dadosConsolidados.put(bankType.name().toLowerCase(), Map.of("erro", "Falha ao buscar dados"));
                }
            }
            
        } catch (Exception e) {
            log.error("Erro ao buscar dados consolidados para usuário {}", userId, e);
        }
        
        return dadosConsolidados;
    }

    /**
     * Renova token expirado usando refresh token
     * 
     * @param autorizacao Autorização que precisa ser renovada
     * @return true se renovação for bem-sucedida
     */
    private boolean renovarTokenExpirado(AutorizacaoBancaria autorizacao) {
        try {
            BankType bankType = mapTipoBanco(autorizacao.getBanco());
            
            // Obtém configurações do banco
            String tokenUrl = "";
            String clientId = "";
            String clientSecret = "";

            switch (bankType) {
                case NUBANK:
                    tokenUrl = nubankTokenUrl;
                    clientId = nubankClientId;
                    clientSecret = nubankClientSecret;
                    break;
                case MERCADO_PAGO:
                    tokenUrl = mercadoPagoTokenUrl;
                    clientId = mercadoPagoClientId;
                    clientSecret = mercadoPagoClientSecret;
                    break;
                case ITAU:
                    tokenUrl = itauTokenUrl;
                    clientId = itauClientId;
                    clientSecret = itauClientSecret;
                    break;
                case INTER:
                    tokenUrl = interTokenUrl;
                    clientId = interClientId;
                    clientSecret = interClientSecret;
                    break;
            }

            // Configura headers de autenticação
            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(clientId, clientSecret);
            headers.set("Content-Type", "application/x-www-form-urlencoded");

            // Monta corpo da requisição para renovação
            String body = String.format("grant_type=refresh_token&refresh_token=%s", autorizacao.getRefreshToken());

            // Cria entidade HTTP
            HttpEntity<String> request = new HttpEntity<>(body, headers);

            // Envia requisição para renovar token
            ResponseEntity<Map> response = restTemplate.exchange(tokenUrl, HttpMethod.POST, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
                // Extrai novos tokens da resposta
                String newAccessToken = (String) responseBody.get("access_token");
                String newRefreshToken = (String) responseBody.get("refresh_token");
                Integer expiresIn = (Integer) responseBody.get("expires_in");
                
                if (newAccessToken != null) {
                    // Atualiza a autorização com os novos tokens
                    autorizacao.setAccessToken(newAccessToken);
                    
                    if (newRefreshToken != null) {
                        autorizacao.setRefreshToken(newRefreshToken);
                    }
                    
                    // Calcula nova data de expiração
                    if (expiresIn != null) {
                        LocalDateTime novaDataExpiracao = LocalDateTime.now().plusSeconds(expiresIn);
                        autorizacao.setDataExpiracao(novaDataExpiracao);
                    } else {
                        // Fallback: expira em 1 hora se não informado
                        autorizacao.setDataExpiracao(LocalDateTime.now().plusHours(1));
                    }
                    
                    // Salva a autorização atualizada
                    Map<String, Object> tokenResponse = new HashMap<>();
                    tokenResponse.put("access_token", newAccessToken);
                    tokenResponse.put("refresh_token", newRefreshToken);
                    tokenResponse.put("expires_in", expiresIn);
                    autorizacaoBancariaService.renovarToken(autorizacao, tokenResponse);
                    
                    log.info("✅ Token renovado com sucesso para autorização {} - banco {}", 
                            autorizacao.getId(), bankType);
                    return true;
                } else {
                    log.error("❌ Resposta de renovação não contém access_token para autorização {}", 
                            autorizacao.getId());
                    return false;
                }
            } else {
                log.error("❌ Falha na renovação de token - Status: {} para autorização {}", 
                        response.getStatusCode(), autorizacao.getId());
                return false;
            }
            
        } catch (Exception e) {
            log.error("❌ Erro ao renovar token para autorização {}: {}", 
                    autorizacao.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Mapeia o nome do banco do modelo para o enum do serviço
     * 
     * @param nomeBanco Nome do banco do modelo
     * @return Tipo do banco do serviço
     */
    private BankType mapTipoBanco(String nomeBanco) {
        switch (nomeBanco.toUpperCase()) {
            case "NUBANK":
                return BankType.NUBANK;
            case "ITAU":
                return BankType.ITAU;
            case "INTER":
                return BankType.INTER;
            case "MERCADO_PAGO":
                return BankType.MERCADO_PAGO;
            default:
                throw new IllegalArgumentException("Banco não suportado: " + nomeBanco);
        }
    }
}
