package com.consumoesperto.controller;

import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.BankApiService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
@RequestMapping("/api/bank") // Base path para endpoints de integração bancária
@RequiredArgsConstructor // Lombok: gera construtor com campos final
@Api(tags = "Integração com APIs Bancárias") // Documentação Swagger
@CrossOrigin(origins = "*") // Permite CORS de qualquer origem
public class BankApiController {

    // Serviço responsável pela integração com APIs bancárias
    private final BankApiService bankApiService;

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
    @ApiOperation("Obter URL de autorização para banco")
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
    @ApiOperation("Trocar código de autorização por token de acesso")
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
    @ApiOperation("Obter saldo da conta")
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
    @ApiOperation("Obter transações da conta")
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
    @ApiOperation("Obter cartões de crédito")
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
    @ApiOperation("Obter faturas do cartão de crédito")
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
    @ApiOperation("Renovar token de acesso")
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
    @ApiOperation("Obter saldo real da conta bancária")
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
    @ApiOperation("Obter cartões de crédito reais")
    public ResponseEntity<Map<String, Object>> getRealCreditCards(
            @PathVariable BankApiService.BankType bankType,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
            // Busca cartões reais usando autorizações salvas
            Map<String, Object> cartoes = bankApiService.getRealCreditCards(currentUser.getId(), bankType);
            
            if (cartoes != null) {
                return ResponseEntity.ok(cartoes);
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("erro", "Falha ao buscar cartões reais"));
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
    @ApiOperation("Obter faturas reais do cartão de crédito")
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
    @ApiOperation("Obter transações reais da conta")
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
    @ApiOperation("Obter dados consolidados de todos os bancos")
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
}
