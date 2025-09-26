package com.consumoesperto.controller;

import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.BankApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.consumoesperto.model.BankApiConfig;
import lombok.extern.slf4j.Slf4j;
import javax.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashMap;
import org.springframework.http.HttpStatus;
import com.consumoesperto.repository.BankApiConfigRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.repository.AutorizacaoBancariaRepository;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.model.AutorizacaoBancaria;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import com.consumoesperto.service.MercadoPagoService;
import java.util.Objects;

/**
 * Controller responsável por gerenciar integrações com APIs bancárias
 * 
 * Este controller expõe endpoints para integração com diferentes bancos
 * através de suas APIs oficiais, permitindo sincronização automática
 * de dados financeiros como saldos, transações e cartões de crédito.
 * 
 * Funcionalidades principais:
 * - Autenticação OAuth2 com bancos parceiros
 * - Sincronização de saldos e transações
 * - Obtenção de dados de cartões de crédito
 * - Renovação automática de tokens de acesso
 * - Suporte a múltiplos bancos (Itaú, Nubank, Mercado Pago)
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@RestController
@RequestMapping("/api/bank-api") // Base path para endpoints de integração bancária
@RequiredArgsConstructor // Lombok: gera construtor com campos final
@Slf4j
@Tag(name = "APIs Bancárias", description = "Endpoints para integração com APIs bancárias")
@CrossOrigin(origins = {"http://localhost:4200", "https://22e294954ab2.ngrok-free.app"}) // Permite CORS de qualquer origem
public class BankApiController {

    // Serviço responsável pela integração com APIs bancárias
    private final BankApiService bankApiService;
    private final BankApiConfigRepository bankApiConfigRepository;
    private final UsuarioRepository usuarioRepository;
    private final AutorizacaoBancariaRepository autorizacaoBancariaRepository;
    private final MercadoPagoService mercadopagoService;

    /**
     * Obtém URL de autorização para autenticação com banco específico
     * 
     * Endpoint para iniciar o fluxo OAuth2 com um banco parceiro.
     * Retorna a URL onde o usuário deve ser redirecionado para
     * autorizar o acesso aos dados bancários.
     * 
     * @param bankType Tipo do banco (ITAÚ, NUBANK, MERCADO_PAGO)
     * @param redirectUri URI de redirecionamento após autorização
     * @param state Estado para segurança da transação OAuth2
     * @return URL de autorização do banco
     */
    @GetMapping("/auth/{bankType}")
    @Operation(summary = "Obter URL de autorização", description = "Obtém URL de autorização para autenticação com banco")
    public ResponseEntity<String> getAuthorizationUrl(
            @PathVariable BankApiService.BankType bankType,
            @RequestParam String redirectUri,
            @RequestParam String state) {
        
        // Obtém URL de autorização através do serviço bancário
        String authUrl = bankApiService.getAuthorizationUrl(bankType, redirectUri, state);
        return ResponseEntity.ok(authUrl);
    }

    /**
     * Troca código de autorização por token de acesso
     * 
     * Endpoint para completar o fluxo OAuth2, trocando o código
     * de autorização retornado pelo banco por um token de acesso
     * que permite consultar dados bancários.
     * 
     * @param bankType Tipo do banco (ITAÚ, NUBANK, MERCADO_PAGO)
     * @param code Código de autorização retornado pelo banco
     * @param redirectUri URI de redirecionamento (deve ser igual ao usado na autorização)
     * @return Resposta contendo token de acesso e refresh token
     */
    @PostMapping("/token/{bankType}")
    @Operation(summary = "Trocar código por token", description = "Troca código de autorização por token de acesso")
    public ResponseEntity<Map<String, Object>> exchangeCodeForToken(
            @PathVariable BankApiService.BankType bankType,
            @RequestParam String code,
            @RequestParam String redirectUri) {
        
        // Troca código por token através do serviço bancário
        Map<String, Object> tokenResponse = bankApiService.exchangeCodeForToken(bankType, code, redirectUri);
        return ResponseEntity.ok(tokenResponse);
    }

    /**
     * Obtém saldo atual da conta bancária
     * 
     * Endpoint para consultar o saldo disponível na conta do usuário
     * em um banco específico, usando o token de acesso válido.
     * 
     * @param bankType Tipo do banco (ITAÚ, NUBANK, MERCADO_PAGO)
     * @param accessToken Token de acesso válido para o banco
     * @param currentUser Usuário autenticado (para validação)
     * @return Dados do saldo da conta bancária
     */
    @GetMapping("/balance/{bankType}")
    @Operation(summary = "Obter saldo da conta", description = "Obtém saldo atual da conta bancária")
    public ResponseEntity<Map<String, Object>> getAccountBalance(
            @PathVariable BankApiService.BankType bankType,
            @RequestParam String accessToken,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Obtém saldo através do serviço bancário
        Map<String, Object> balance = bankApiService.getAccountBalance(bankType, accessToken);
        return ResponseEntity.ok(balance);
    }

    /**
     * Obtém transações da conta bancária
     * 
     * Endpoint para sincronizar transações bancárias do usuário,
     * permitindo análise detalhada de movimentações financeiras
     * e categorização automática de gastos.
     * 
     * @param bankType Tipo do banco (ITAÚ, NUBANK, MERCADO_PAGO)
     * @param accessToken Token de acesso válido para o banco
     * @param accountId ID da conta bancária
     * @param currentUser Usuário autenticado (para validação)
     * @return Lista de transações bancárias
     */
    @GetMapping("/transactions/{bankType}")
    @Operation(summary = "Obter transações", description = "Obtém transações da conta bancária")
    public ResponseEntity<Map<String, Object>> getTransactions(
            @PathVariable BankApiService.BankType bankType,
            @RequestParam String accessToken,
            @RequestParam String accountId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Obtém transações através do serviço bancário
        Map<String, Object> transactions = bankApiService.getTransactions(bankType, accessToken, accountId);
        return ResponseEntity.ok(transactions);
    }

    /**
     * Obtém cartões de crédito do banco
     * 
     * Endpoint para sincronizar informações de cartões de crédito,
     * incluindo limites, faturas e status de cada cartão.
     * 
     * @param bankType Tipo do banco (ITAÚ, NUBANK, MERCADO_PAGO)
     * @param accessToken Token de acesso válido para o banco
     * @param currentUser Usuário autenticado (para validação)
     * @return Lista de cartões de crédito do banco
     */
    @GetMapping("/credit-cards/{bankType}")
    @Operation(summary = "Obter cartões de crédito", description = "Obtém cartões de crédito do banco")
    public ResponseEntity<Map<String, Object>> getCreditCards(
            @PathVariable BankApiService.BankType bankType,
            @RequestParam String accessToken,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Obtém cartões de crédito através do serviço bancário
        Map<String, Object> creditCards = bankApiService.getCreditCards(bankType, accessToken);
        return ResponseEntity.ok(creditCards);
    }

    /**
     * Obtém faturas de um cartão de crédito específico
     * 
     * Endpoint para sincronizar faturas de cartão de crédito,
     * incluindo valores, datas de vencimento e status de pagamento.
     * 
     * @param bankType Tipo do banco (ITAÚ, NUBANK, MERCADO_PAGO)
     * @param cardId ID do cartão de crédito
     * @param accessToken Token de acesso válido para o banco
     * @param currentUser Usuário autenticado (para validação)
     * @return Lista de faturas do cartão de crédito
     */
    @GetMapping("/credit-cards/{bankType}/{cardId}/invoices")
    @Operation(summary = "Obter faturas do cartão de crédito", description = "Retorna faturas de um cartão de crédito específico")
    public ResponseEntity<Map<String, Object>> getCreditCardInvoices(
            @PathVariable BankApiService.BankType bankType,
            @PathVariable String cardId,
            @RequestParam String accessToken,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Obtém faturas através do serviço bancário
        Map<String, Object> invoices = bankApiService.getCreditCardInvoices(bankType, accessToken, cardId);
        return ResponseEntity.ok(invoices);
    }

    /**
     * Renova token de acesso usando refresh token
     * 
     * Endpoint para renovar automaticamente tokens de acesso expirados,
     * garantindo continuidade na sincronização de dados bancários
     * sem necessidade de nova autorização do usuário.
     * 
     * @param bankType Tipo do banco (ITAÚ, NUBANK, MERCADO_PAGO)
     * @param refreshToken Refresh token válido para renovação
     * @return Status da renovação (true se bem-sucedida)
     */
    @PostMapping("/refresh-token/{bankType}")
    @Operation(summary = "Renovar token de acesso", description = "Renova token de acesso usando refresh token")
    public ResponseEntity<Boolean> refreshToken(
            @PathVariable BankApiService.BankType bankType,
            @RequestParam String refreshToken) {
        
        // Renova token através do serviço bancário
        boolean success = bankApiService.refreshTokenIfNeeded(bankType, refreshToken);
        return ResponseEntity.ok(success);
    }

    // ===== NOVOS ENDPOINTS PARA DADOS REAIS DOS BANCOS =====

    /**
     * Obtém saldo real da conta bancária usando autorização salva
     * 
     * Endpoint para consultar o saldo real da conta do usuário
     * em um banco específico, usando as autorizações OAuth2 salvas.
     * 
     * @param bankType Tipo do banco (ITAÚ, NUBANK, MERCADO_PAGO)
     * @param currentUser Usuário autenticado
     * @return Dados reais do saldo da conta bancária
     */
    @GetMapping("/real/balance/{bankType}")
    @Operation(summary = "Obter saldo real da conta bancária", description = "Retorna saldo real da conta bancária usando autorização salva")
    public ResponseEntity<Map<String, Object>> getRealAccountBalance(
            @PathVariable BankApiService.BankType bankType,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
            // Busca saldo real usando autorizações salvas
            Map<String, Object> saldo = bankApiService.getRealAccountBalance(currentUser.getId(), bankType);
            
            if (saldo != null) {
                return ResponseEntity.ok(saldo);
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("erro", "Falha ao buscar saldo real"));
        }
    }

    /**
     * Obtém cartões de crédito reais usando autorização salva
     * 
     * Endpoint para consultar os cartões de crédito reais do usuário
     * em um banco específico, usando as autorizações OAuth2 salvas.
     * 
     * @param bankType Tipo do banco (ITAÚ, NUBANK, MERCADO_PAGO)
     * @param currentUser Usuário autenticado
     * @return Lista real de cartões de crédito
     */
    @GetMapping("/real/credit-cards/{bankType}")
    @Operation(summary = "Obter cartões de crédito reais", description = "Retorna cartões de crédito reais usando autorização salva")
    public ResponseEntity<Map<String, Object>> getRealCreditCards(
            @PathVariable String bankType,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
            // Converter String para enum BankType
            BankApiService.BankType bankTypeEnum;
            try {
                bankTypeEnum = BankApiService.BankType.valueOf(bankType.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Mapear valores antigos para novos
                switch (bankType.toUpperCase()) {
                    case "MERCADOPAGO":
                        bankTypeEnum = BankApiService.BankType.MERCADO_PAGO;
                        break;
                    case "ITAU":
                        bankTypeEnum = BankApiService.BankType.ITAU;
                        break;
                    case "INTER":
                        bankTypeEnum = BankApiService.BankType.INTER;
                        break;
                    case "NUBANK":
                        bankTypeEnum = BankApiService.BankType.NUBANK;
                        break;
                    default:
                        return ResponseEntity.badRequest().body(Map.of("erro", "Tipo de banco não suportado: " + bankType));
                }
            }
            
            // Busca cartões reais usando autorizações salvas
            Map<String, Object> cartoes = bankApiService.getRealCreditCards(currentUser.getId(), bankTypeEnum);
            
            if (cartoes != null) {
                return ResponseEntity.ok(cartoes);
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar cartões reais para {}: {}", bankType, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("erro", "Falha ao buscar cartões reais: " + e.getMessage()));
        }
    }

    /**
     * Obtém faturas reais de um cartão de crédito
     * 
     * Endpoint para consultar as faturas reais de um cartão específico
     * usando as autorizações OAuth2 salvas.
     * 
     * @param bankType Tipo do banco (ITAÚ, NUBANK, MERCADO_PAGO)
     * @param cardId ID do cartão de crédito
     * @param currentUser Usuário autenticado
     * @return Lista real de faturas do cartão
     */
    @GetMapping("/real/credit-cards/{bankType}/{cardId}/invoices")
    @Operation(summary = "Obter faturas reais do cartão de crédito", description = "Retorna faturas reais de um cartão de crédito específico")
    public ResponseEntity<Map<String, Object>> getRealCreditCardInvoices(
            @PathVariable BankApiService.BankType bankType,
            @PathVariable String cardId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
                    // Busca faturas reais usando autorizações salvas
        Map<String, Object> faturas = bankApiService.getRealCreditCardInvoices(currentUser.getId(), bankType, cardId);
            
            if (faturas != null) {
                return ResponseEntity.ok(faturas);
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("erro", "Falha ao buscar faturas reais"));
        }
    }

    /**
     * Obtém transações reais de uma conta
     * 
     * Endpoint para consultar as transações reais de uma conta
     * usando as autorizações OAuth2 salvas.
     * 
     * @param bankType Tipo do banco (ITAÚ, NUBANK, MERCADO_PAGO)
     * @param accountId ID da conta bancária
     * @param currentUser Usuário autenticado
     * @return Lista real de transações da conta
     */
    @GetMapping("/real/transactions/{bankType}")
    @Operation(summary = "Obter transações reais da conta", description = "Retorna transações reais de uma conta bancária")
    public ResponseEntity<Map<String, Object>> getRealTransactions(
            @PathVariable BankApiService.BankType bankType,
            @RequestParam String accountId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
            // Busca transações reais usando autorizações salvas
            Map<String, Object> transacoes = bankApiService.getRealTransactions(currentUser.getId(), bankType, accountId);
            
            if (transacoes != null) {
                return ResponseEntity.ok(transacoes);
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("erro", "Falha ao buscar transações reais"));
        }
    }

    /**
     * Obtém dados consolidados de todos os bancos do usuário
     * 
     * Endpoint para consultar dados consolidados de todos os bancos
     * onde o usuário possui autorizações ativas.
     * 
     * @param currentUser Usuário autenticado
     * @return Dados consolidados de todos os bancos
     */
    @GetMapping("/real/consolidated")
    @Operation(summary = "Obter dados consolidados de todos os bancos", description = "Retorna dados consolidados de todos os bancos do usuário")
    public ResponseEntity<Map<String, Object>> getConsolidatedBankData(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
            // Busca dados consolidados usando autorizações salvas
            Map<String, Object> dadosConsolidados = bankApiService.getConsolidatedBankData(currentUser.getId());
            return ResponseEntity.ok(dadosConsolidados);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("erro", "Falha ao buscar dados consolidados"));
        }
    }

    @GetMapping("/configs")
    public ResponseEntity<List<BankApiConfig>> getConfigs() {
        try {
            log.info("🔍 Buscando todas as configurações bancárias...");
            
            // Buscar TODAS as configurações (ativas e inativas)
            List<BankApiConfig> configs = bankApiConfigRepository.findAll();
            
            log.info("✅ {} configurações bancárias encontradas", configs.size());
            return ResponseEntity.ok(configs);
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar configurações bancárias: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Endpoint de teste para verificar conectividade
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testConnection() {
        try {
            log.info("🧪 Teste de conectividade solicitado");
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Backend funcionando corretamente");
            response.put("timestamp", LocalDateTime.now());
            response.put("configsCount", bankApiConfigRepository.count());
            
            log.info("✅ Teste de conectividade bem-sucedido");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Erro no teste de conectividade: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "Erro no backend: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @PostMapping("/configs")
    @Operation(summary = "Criar configuração bancária", description = "Cria uma nova configuração para integração com banco")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<BankApiConfig> createConfig(@Valid @RequestBody BankApiConfig config, @AuthenticationPrincipal UserPrincipal currentUser) {
        try {
            log.info("🆕 Criando nova configuração bancária para usuário: {}", currentUser.getId());
            
            // Garantir que o usuário associado à configuração é o usuário autenticado
            Usuario user = usuarioRepository.findById(currentUser.getId())
                    .orElseThrow(() -> new RuntimeException("Usuário não encontrado com ID: " + currentUser.getId()));
            config.setUsuario(user);
            
            // CORREÇÃO: Preencher AMBAS as colunas (banco e tipo_banco) para compatibilidade
            // Priorizar tipo_banco se estiver preenchido, senão usar banco, senão usar padrão
            String bancoValue = null;
            if (config.getTipoBanco() != null && !config.getTipoBanco().trim().isEmpty()) {
                bancoValue = config.getTipoBanco();
            } else if (config.getBanco() != null && !config.getBanco().trim().isEmpty()) {
                bancoValue = config.getBanco();
            } else {
                bancoValue = "MERCADO_PAGO"; // Valor padrão
            }
            
            // Garantir que AMBAS as colunas sejam preenchidas
            config.setBanco(bancoValue); // Coluna NOT NULL
            config.setTipoBanco(bancoValue); // Coluna nullable
            
            // Preencher todos os campos necessários para PRODUÇÃO
            config.setAuthUrl("https://api.mercadopago.com/authorization");
            config.setTokenUrl("https://api.mercadopago.com/oauth/token");
            config.setSandbox(false); // PRODUÇÃO
            config.setTimeoutMs(30000);
            config.setMaxRetries(3);
            config.setRetryDelayMs(1000);
            
            // Definir timestamps
            config.setDataCriacao(LocalDateTime.now());
            config.setDataAtualizacao(LocalDateTime.now());
            
            // Salvar configuração
            BankApiConfig savedConfig = bankApiConfigRepository.save(config);
            log.info("✅ Configuração bancária criada com sucesso. ID: {}", savedConfig.getId());
            
            // Criar autorização bancária automaticamente
            criarAutorizacaoBancaria(savedConfig);
            
            // Se for Mercado Pago e estiver ativa, iniciar sincronização automática
            if ("MERCADOPAGO".equals(savedConfig.getBanco()) && savedConfig.getAtivo()) {
                try {
                    log.info("🚀 Iniciando sincronização automática para Mercado Pago...");
                    
                    // Executar em thread separada para não bloquear a resposta
                    CompletableFuture.runAsync(() -> {
                        try {
                            // Aguardar um pouco para garantir que a configuração foi salva
                            Thread.sleep(1000);
                            
                            // Chamar sincronização automática
                            mercadopagoService.sincronizarDadosAutomaticamente(savedConfig.getUsuario().getId());
                            
                        } catch (Exception e) {
                            log.error("❌ Erro na sincronização automática: {}", e.getMessage(), e);
                        }
                    });
                    
                    log.info("✅ Sincronização automática iniciada em background");
                    
                } catch (Exception e) {
                    log.error("❌ Erro ao iniciar sincronização automática: {}", e.getMessage(), e);
                }
            }
            
            return ResponseEntity.status(HttpStatus.CREATED).body(savedConfig);
            
        } catch (Exception e) {
            log.error("❌ Erro ao criar configuração bancária: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao criar configuração bancária", e);
        }
    }

    @PutMapping("/configs/{id}")
    @Operation(summary = "Atualizar configuração bancária", description = "Atualiza uma configuração existente para integração com banco")
    @PreAuthorize("hasRole('USER') and @securityService.canUpdateBankConfig(#id)")
    public ResponseEntity<BankApiConfig> updateConfig(@PathVariable Long id, @Valid @RequestBody BankApiConfig config, @AuthenticationPrincipal UserPrincipal currentUser) {
        try {
            log.info("🔄 Atualizando configuração bancária ID: {} para usuário: {}", id, currentUser.getId());
            
            // Verificar se a configuração existe
            Optional<BankApiConfig> existingConfig = bankApiConfigRepository.findById(id);
            if (existingConfig.isEmpty()) {
                log.warn("⚠️ Configuração bancária não encontrada. ID: {}", id);
                return ResponseEntity.notFound().build();
            }
            
            BankApiConfig currentConfig = existingConfig.get();
            
            // Garantir que o usuário associado à configuração é o usuário autenticado
            // Isso previne que o campo 'usuario' seja sobrescrito com null ou um valor incorreto
            // e garante que a configuração sempre pertença ao usuário logado.
            Usuario user = usuarioRepository.findById(currentUser.getId())
                    .orElseThrow(() -> new RuntimeException("Usuário não encontrado com ID: " + currentUser.getId()));
            currentConfig.setUsuario(user); // Define o usuário antes de verificar ou atualizar
            
            // Verificar se o usuário atual é o proprietário da configuração
            if (!currentConfig.getUsuario().getId().equals(currentUser.getId())) {
                log.warn("⚠️ Usuário {} não tem permissão para atualizar configuração ID {}", currentUser.getId(), id);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            // Verificar se houve mudança no Client Secret (para Mercado Pago)
            boolean clientSecretChanged = !Objects.equals(currentConfig.getClientSecret(), config.getClientSecret());
            boolean wasInactive = !currentConfig.getAtivo();
            boolean isNowActive = config.getAtivo();
            
            // Atualizar campos
            currentConfig.setClientId(config.getClientId());
            currentConfig.setClientSecret(config.getClientSecret());
            currentConfig.setUserId(config.getUserId());
            currentConfig.setApiUrl(config.getApiUrl());
            currentConfig.setAuthUrl(config.getAuthUrl());
            currentConfig.setTokenUrl(config.getTokenUrl());
            currentConfig.setRedirectUri(config.getRedirectUri());
            currentConfig.setScope(config.getScope());
            currentConfig.setSandbox(config.getSandbox());
            currentConfig.setAtivo(config.getAtivo());
            currentConfig.setTimeoutMs(config.getTimeoutMs());
            currentConfig.setMaxRetries(config.getMaxRetries());
            currentConfig.setRetryDelayMs(config.getRetryDelayMs());
            
            // CORREÇÃO: Atualizar AMBAS as colunas (banco e tipo_banco) para compatibilidade
            String bancoValue = null;
            if (config.getTipoBanco() != null && !config.getTipoBanco().trim().isEmpty()) {
                bancoValue = config.getTipoBanco();
            } else if (config.getBanco() != null && !config.getBanco().trim().isEmpty()) {
                bancoValue = config.getBanco();
            } else {
                // Manter valor atual se não houver mudança
                bancoValue = currentConfig.getBanco() != null ? currentConfig.getBanco() : "MERCADO_PAGO";
            }
            
            // Garantir que AMBAS as colunas sejam preenchidas
            currentConfig.setBanco(bancoValue); // Coluna NOT NULL
            currentConfig.setTipoBanco(bancoValue); // Coluna nullable
            
            // Garantir que todos os campos de PRODUÇÃO estejam preenchidos
            if (currentConfig.getAuthUrl() == null) {
                currentConfig.setAuthUrl("https://api.mercadopago.com/authorization");
            }
            if (currentConfig.getTokenUrl() == null) {
                currentConfig.setTokenUrl("https://api.mercadopago.com/oauth/token");
            }
            if (currentConfig.getSandbox() == null) {
                currentConfig.setSandbox(false); // PRODUÇÃO
            }
            if (currentConfig.getTimeoutMs() == null) {
                currentConfig.setTimeoutMs(30000);
            }
            if (currentConfig.getMaxRetries() == null) {
                currentConfig.setMaxRetries(3);
            }
            if (currentConfig.getRetryDelayMs() == null) {
                currentConfig.setRetryDelayMs(1000);
            }
            
            currentConfig.setDataAtualizacao(LocalDateTime.now());
            
            // Salvar configuração atualizada
            BankApiConfig savedConfig = bankApiConfigRepository.save(currentConfig);
            log.info("✅ Configuração bancária atualizada com sucesso. ID: {}", savedConfig.getId());
            
            // Atualizar autorização bancária automaticamente
            criarAutorizacaoBancaria(savedConfig);
            
            // Se for Mercado Pago e houve mudanças significativas, iniciar sincronização automática
            if ("MERCADOPAGO".equals(savedConfig.getBanco()) && 
                (clientSecretChanged || (wasInactive && isNowActive))) {
                
                try {
                    log.info("🚀 Iniciando sincronização automática para Mercado Pago (configuração atualizada)...");
                    
                    // Se o client_secret foi alterado e não é placeholder, atualizar autorizacoes_bancarias
                    if (clientSecretChanged && !"CONFIGURAR_CLIENT_SECRET".equals(config.getClientSecret())) {
                        log.info("🔑 Client Secret alterado, atualizando tabela autorizacoes_bancarias...");
                        atualizarAutorizacaoBancaria(savedConfig.getUsuario().getId(), config.getClientSecret());
                    }
                    
                    // Executar em thread separada para não bloquear a resposta
                    CompletableFuture.runAsync(() -> {
                        try {
                            // Aguardar um pouco para garantir que a configuração foi salva
                            Thread.sleep(1000);
                            
                            // Chamar sincronização automática
                            mercadopagoService.sincronizarDadosAutomaticamente(savedConfig.getUsuario().getId());
                            
                        } catch (Exception e) {
                            log.error("❌ Erro na sincronização automática: {}", e.getMessage(), e);
                        }
                    });
                    
                    log.info("✅ Sincronização automática iniciada em background após atualização");
                    
                } catch (Exception e) {
                    log.error("❌ Erro ao iniciar sincronização automática: {}", e.getMessage(), e);
                }
            }
            
            return ResponseEntity.ok(savedConfig);
            
        } catch (Exception e) {
            log.error("❌ Erro ao atualizar configuração bancária ID {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Erro ao atualizar configuração bancária", e);
        }
    }

    @DeleteMapping("/configs/{id}")
    @PreAuthorize("hasRole('USER') and @securityService.canDeleteBankConfig(#id)")
    public ResponseEntity<Void> deleteConfig(@PathVariable Long id) {
        try {
            // Implementar exclusão de configuração
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Erro ao deletar configuração bancária: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/sync")
    @PreAuthorize("hasRole('USER') and @securityService.canSyncBankData(#usuarioId)")
    public ResponseEntity<Map<String, Object>> syncBankData(@RequestParam Long usuarioId, @RequestParam String bankCode) {
        try {
            // Implementar sincronização de dados bancários
            return ResponseEntity.ok(Map.of("status", "Sincronização iniciada"));
        } catch (Exception e) {
            log.error("Erro ao sincronizar dados bancários: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("erro", "Falha na sincronização"));
        }
    }

    /**
     * Força sincronização manual dos dados do Mercado Pago
     */
    @PostMapping("/sync/mercadopago/{usuarioId}")
    @Operation(summary = "Sincronizar dados Mercado Pago", description = "Força sincronização manual dos dados do Mercado Pago")
    @PreAuthorize("hasRole('USER') and @securityService.canSyncBankData(#usuarioId)")
    public ResponseEntity<Map<String, Object>> syncMercadoPagoData(@PathVariable Long usuarioId) {
        try {
            log.info("🔄 Iniciando sincronização manual dos dados do Mercado Pago para usuário: {}", usuarioId);
            
            // Verificar se a configuração está válida
            Optional<BankApiConfig> config = bankApiConfigRepository.findByUsuarioIdAndBanco(usuarioId, "MERCADOPAGO");
            if (config.isEmpty()) {
                log.warn("⚠️ Configuração do Mercado Pago não encontrada para usuário: {}", usuarioId);
                return ResponseEntity.badRequest().body(Map.of(
                    "erro", "Configuração do Mercado Pago não encontrada",
                    "usuarioId", usuarioId
                ));
            }
            
            BankApiConfig mercadopagoConfig = config.get();
            if (!mercadopagoConfig.getAtivo()) {
                log.warn("⚠️ Configuração do Mercado Pago inativa para usuário: {}", usuarioId);
                return ResponseEntity.badRequest().body(Map.of(
                    "erro", "Configuração do Mercado Pago está inativa",
                    "usuarioId", usuarioId
                ));
            }
            
            if ("CONFIGURAR_CLIENT_SECRET".equals(mercadopagoConfig.getClientSecret())) {
                log.warn("⚠️ Client Secret não configurado para usuário: {}", usuarioId);
                return ResponseEntity.badRequest().body(Map.of(
                    "erro", "Client Secret do Mercado Pago não foi configurado",
                    "usuarioId", usuarioId
                ));
            }
            
            // Executar sincronização em thread separada
            CompletableFuture.runAsync(() -> {
                try {
                    mercadopagoService.sincronizarDadosAutomaticamente(usuarioId);
                } catch (Exception e) {
                    log.error("❌ Erro na sincronização manual: {}", e.getMessage(), e);
                }
            });
            
            log.info("✅ Sincronização manual iniciada com sucesso para usuário: {}", usuarioId);
            
            return ResponseEntity.ok(Map.of(
                "mensagem", "Sincronização iniciada com sucesso",
                "usuarioId", usuarioId,
                "status", "em_andamento"
            ));
            
        } catch (Exception e) {
            log.error("❌ Erro ao iniciar sincronização manual para usuário {}: {}", usuarioId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "erro", "Erro interno ao iniciar sincronização",
                "usuarioId", usuarioId
            ));
        }
    }

    @GetMapping("/debug/mercadopago")
    public ResponseEntity<Map<String, Object>> debugMercadoPago(@RequestParam Long usuarioId) {
        try {
            log.info("🔍 Debug: Verificando configuração Mercado Pago para usuário: {}", usuarioId);
            
            Optional<BankApiConfig> config = bankApiConfigRepository.findByUsuarioIdAndBanco(usuarioId, "MERCADOPAGO");
            
            Map<String, Object> response = new HashMap<>();
            response.put("usuarioId", usuarioId);
            response.put("configuracaoEncontrada", config.isPresent());
            
            if (config.isPresent()) {
                BankApiConfig cfg = config.get();
                response.put("id", cfg.getId());
                response.put("banco", cfg.getBanco());
                response.put("clientId", cfg.getClientId());
                response.put("clientSecret", cfg.getClientSecret() != null ? 
                    (cfg.getClientSecret().length() > 10 ? 
                        cfg.getClientSecret().substring(0, 10) + "..." : 
                        cfg.getClientSecret()) : "NULL");
                response.put("userId", cfg.getUserId());
                response.put("ativo", cfg.getAtivo());
                response.put("dataCriacao", cfg.getDataCriacao());
                response.put("dataAtualizacao", cfg.getDataAtualizacao());
                
                // Verificar se é placeholder
                boolean isPlaceholder = "CONFIGURAR_CLIENT_SECRET".equals(cfg.getClientSecret());
                response.put("isPlaceholder", isPlaceholder);
                response.put("clientSecretLength", cfg.getClientSecret() != null ? cfg.getClientSecret().length() : 0);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Erro ao debugar configuração Mercado Pago: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("erro", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping("/debug/public/mercadopago")
    public ResponseEntity<Map<String, Object>> debugMercadoPagoPublic(@RequestParam Long usuarioId) {
        try {
            log.info("🔍 Debug PÚBLICO: Verificando configuração Mercado Pago para usuário: {}", usuarioId);
            
            Optional<BankApiConfig> config = bankApiConfigRepository.findByUsuarioIdAndBanco(usuarioId, "MERCADOPAGO");
            
            Map<String, Object> response = new HashMap<>();
            response.put("usuarioId", usuarioId);
            response.put("configuracaoEncontrada", config.isPresent());
            
            if (config.isPresent()) {
                BankApiConfig cfg = config.get();
                response.put("id", cfg.getId());
                response.put("banco", cfg.getBanco());
                response.put("clientId", cfg.getClientId());
                response.put("clientSecret", cfg.getClientSecret() != null ? 
                    (cfg.getClientSecret().length() > 10 ? 
                        cfg.getClientSecret().substring(0, 10) + "..." : 
                        cfg.getClientSecret()) : "NULL");
                response.put("userId", cfg.getUserId());
                response.put("ativo", cfg.getAtivo());
                response.put("dataCriacao", cfg.getDataCriacao());
                response.put("dataAtualizacao", cfg.getDataAtualizacao());
                
                // Verificar se é placeholder
                boolean isPlaceholder = "CONFIGURAR_CLIENT_SECRET".equals(cfg.getClientSecret());
                response.put("isPlaceholder", isPlaceholder);
                response.put("clientSecretLength", cfg.getClientSecret() != null ? cfg.getClientSecret().length() : 0);
                
                // Adicionar informações de debug adicionais
                response.put("clientSecretExato", cfg.getClientSecret());
                response.put("clientSecretIgualPlaceholder", "CONFIGURAR_CLIENT_SECRET".equals(cfg.getClientSecret()));
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Erro ao debugar configuração Mercado Pago (público): {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("erro", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PostMapping("/debug/public/mercadopago/fix")
    public ResponseEntity<Map<String, Object>> fixMercadoPagoConfig(@RequestParam Long usuarioId, @RequestParam String clientSecret) {
        try {
            log.info("🔧 Fix PÚBLICO: Corrigindo configuração Mercado Pago para usuário: {} com clientSecret: {}", usuarioId, clientSecret);
            
            Optional<BankApiConfig> config = bankApiConfigRepository.findByUsuarioIdAndBanco(usuarioId, "MERCADOPAGO");
            
            if (config.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "erro", "Configuração não encontrada",
                    "usuarioId", usuarioId
                ));
            }
            
            BankApiConfig cfg = config.get();
            String oldClientSecret = cfg.getClientSecret();
            
            // Atualizar client secret
            cfg.setClientSecret(clientSecret);
            cfg.setDataAtualizacao(LocalDateTime.now());
            
            BankApiConfig savedConfig = bankApiConfigRepository.save(cfg);
            
            log.info("✅ Client Secret atualizado de '{}' para '{}'", oldClientSecret, clientSecret);
            
            // Iniciar sincronização automática
            CompletableFuture.runAsync(() -> {
                try {
                    log.info("🚀 Iniciando sincronização automática após correção...");
                    mercadopagoService.sincronizarDadosAutomaticamente(usuarioId);
                } catch (Exception e) {
                    log.error("❌ Erro na sincronização automática: {}", e.getMessage(), e);
                }
            });
            
            return ResponseEntity.ok(Map.of(
                "mensagem", "Client Secret corrigido com sucesso",
                "usuarioId", usuarioId,
                "oldClientSecret", oldClientSecret,
                "newClientSecret", clientSecret,
                "sincronizacaoIniciada", true
            ));
            
        } catch (Exception e) {
            log.error("❌ Erro ao corrigir configuração Mercado Pago: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("erro", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Cria autorização bancária automaticamente quando uma configuração é salva
     * 
     * @param config Configuração bancária salva
     */
    private void criarAutorizacaoBancaria(BankApiConfig config) {
        try {
            log.info("🔑 Criando autorização bancária para configuração ID: {} - Banco: {}", 
                config.getId(), config.getBanco());
            
            // Verificar se já existe autorização para este usuário e banco
            Optional<AutorizacaoBancaria> authExistente = autorizacaoBancariaRepository
                .findByUsuarioIdAndTipoBanco(config.getUsuario().getId(), config.getBanco());
            
            if (authExistente.isPresent()) {
                log.info("⚠️ Autorização bancária já existe para usuário {} e banco {}. Atualizando...", 
                    config.getUsuario().getId(), config.getBanco());
                
                // Atualizar autorização existente
                AutorizacaoBancaria auth = authExistente.get();
                auth.setAccessToken(config.getClientSecret());
                auth.setBanco(config.getBanco()); // Preencher coluna NOT NULL
                auth.setTipoConta("CREDITO"); // Preencher coluna NOT NULL
                auth.setDataAtualizacao(LocalDateTime.now());
                auth.setAtivo(config.getAtivo());
                autorizacaoBancariaRepository.save(auth);
                
                log.info("✅ Autorização bancária atualizada com sucesso");
                return;
            }
            
            // Criar nova autorização bancária
            AutorizacaoBancaria novaAuth = new AutorizacaoBancaria();
            novaAuth.setUsuario(config.getUsuario());
            novaAuth.setTipoBanco(config.getBanco());
            novaAuth.setBanco(config.getBanco()); // Preencher coluna NOT NULL
            novaAuth.setTipoConta("CREDITO"); // Preencher coluna NOT NULL
            novaAuth.setAccessToken(config.getClientSecret());
            novaAuth.setTokenType("Bearer");
            novaAuth.setScope(config.getScope() != null ? config.getScope() : "read,write");
            novaAuth.setAtivo(config.getAtivo());
            novaAuth.setDataCriacao(LocalDateTime.now());
            novaAuth.setDataAtualizacao(LocalDateTime.now());
            
            // Para Mercado Pago, definir data de expiração (6 horas)
            if ("MERCADO_PAGO".equals(config.getBanco())) {
                novaAuth.setDataExpiracao(LocalDateTime.now().plusHours(6));
            }
            
            autorizacaoBancariaRepository.save(novaAuth);
            log.info("✅ Autorização bancária criada com sucesso para usuário {} e banco {}", 
                config.getUsuario().getId(), config.getBanco());
            
        } catch (Exception e) {
            log.error("❌ Erro ao criar autorização bancária para configuração ID {}: {}", 
                config.getId(), e.getMessage(), e);
        }
    }

    /**
     * Atualiza ou cria autorização bancária quando o client_secret é alterado
     * 
     * @param usuarioId ID do usuário
     * @param accessToken Novo access token (client_secret)
     */
    private void atualizarAutorizacaoBancaria(Long usuarioId, String accessToken) {
        try {
            log.info("🔑 Atualizando autorização bancária para usuário: {} com token: {}...", 
                usuarioId, accessToken.substring(0, Math.min(8, accessToken.length())) + "***");
            
            // Buscar autorização existente
            Optional<AutorizacaoBancaria> authOpt = autorizacaoBancariaRepository
                .findByUsuarioIdAndTipoBanco(usuarioId, "MERCADO_PAGO");
            
            if (authOpt.isPresent()) {
                // Atualizar autorização existente
                AutorizacaoBancaria auth = authOpt.get();
                auth.setAccessToken(accessToken);
                auth.setDataAtualizacao(LocalDateTime.now());
                auth.setAtivo(true);
                autorizacaoBancariaRepository.save(auth);
                log.info("✅ Autorização bancária atualizada para usuário: {}", usuarioId);
            } else {
                // Criar nova autorização
                Optional<Usuario> usuarioOpt = usuarioRepository.findById(usuarioId);
                if (usuarioOpt.isPresent()) {
                    AutorizacaoBancaria novaAuth = new AutorizacaoBancaria();
                    novaAuth.setUsuario(usuarioOpt.get());
                    novaAuth.setTipoBanco("MERCADO_PAGO");
                    novaAuth.setAccessToken(accessToken);
                    novaAuth.setTokenType("Bearer");
                    novaAuth.setScope("read,write");
                    novaAuth.setAtivo(true);
                    novaAuth.setDataCriacao(LocalDateTime.now());
                    novaAuth.setDataAtualizacao(LocalDateTime.now());
                    autorizacaoBancariaRepository.save(novaAuth);
                    log.info("✅ Nova autorização bancária criada para usuário: {}", usuarioId);
                } else {
                    log.error("❌ Usuário não encontrado para criar autorização: {}", usuarioId);
                }
            }
            
        } catch (Exception e) {
            log.error("❌ Erro ao atualizar autorização bancária para usuário {}: {}", usuarioId, e.getMessage(), e);
        }
    }
}
