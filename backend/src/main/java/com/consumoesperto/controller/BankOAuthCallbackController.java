package com.consumoesperto.controller;

import com.consumoesperto.service.BankApiService;
import com.consumoesperto.service.AutorizacaoBancariaService;
import com.consumoesperto.model.AutorizacaoBancaria;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller responsável por gerenciar callbacks OAuth2 dos bancos
 * 
 * Este controller recebe as respostas dos bancos após o usuário autorizar
 * o acesso aos dados bancários, processa os códigos de autorização e
 * gerencia os tokens de acesso.
 * 
 * Funcionalidades principais:
 * - Recebimento de callbacks OAuth2 dos bancos
 * - Processamento de códigos de autorização
 * - Troca de códigos por tokens de acesso
 * - Armazenamento seguro de credenciais
 * - Redirecionamento para o frontend
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@RestController
@RequestMapping("/api/bank/oauth")
@RequiredArgsConstructor
@Slf4j
@Api(tags = "Callbacks OAuth2 Bancários")
@CrossOrigin(origins = "*")
public class BankOAuthCallbackController {

    private final BankApiService bankApiService;
    private final AutorizacaoBancariaService autorizacaoBancariaService;

    /**
     * Callback OAuth2 para o Itaú
     * 
     * Recebe o código de autorização do Itaú e processa a autenticação
     * 
     * @param code Código de autorização retornado pelo Itaú
     * @param state Estado para segurança CSRF
     * @param error Erro retornado pelo banco (se houver)
     * @return Redirecionamento para o frontend com resultado
     */
    @GetMapping("/itau/callback")
    @ApiOperation("Callback OAuth2 do Itaú")
    public ResponseEntity<String> itauCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        
        log.info("Callback OAuth2 recebido do Itaú - code: {}, state: {}, error: {}", code, state, error);
        
        if (error != null) {
            log.error("Erro na autorização OAuth2 do Itaú: {}", error);
            return ResponseEntity.ok("Erro na autorização: " + error);
        }
        
        if (code == null) {
            log.error("Código de autorização não recebido do Itaú");
            return ResponseEntity.badRequest().body("Código de autorização não recebido");
        }
        
        try {
            // Processa a autorização
            Map<String, Object> result = processOAuthCallback(BankApiService.BankType.ITAU, code, state);
            return ResponseEntity.ok("Autorização processada com sucesso! Você pode fechar esta janela.");
        } catch (Exception e) {
            log.error("Erro ao processar callback do Itaú", e);
            return ResponseEntity.ok("Erro ao processar autorização: " + e.getMessage());
        }
    }

    /**
     * Callback OAuth2 para o Nubank
     * 
     * Recebe o código de autorização do Nubank e processa a autenticação
     * 
     * @param code Código de autorização retornado pelo Nubank
     * @param state Estado para segurança CSRF
     * @param error Erro retornado pelo banco (se houver)
     * @return Redirecionamento para o frontend com resultado
     */
    @GetMapping("/nubank/callback")
    @ApiOperation("Callback OAuth2 do Nubank")
    public ResponseEntity<String> nubankCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        
        log.info("Callback OAuth2 recebido do Nubank - code: {}, state: {}, error: {}", code, state, error);
        
        if (error != null) {
            log.error("Erro na autorização OAuth2 do Nubank: {}", error);
            return ResponseEntity.ok("Erro na autorização: " + error);
        }
        
        if (code == null) {
            log.error("Código de autorização não recebido do Nubank");
            return ResponseEntity.badRequest().body("Código de autorização não recebido");
        }
        
        try {
            // Processa a autorização
            Map<String, Object> result = processOAuthCallback(BankApiService.BankType.NUBANK, code, state);
            return ResponseEntity.ok("Autorização processada com sucesso! Você pode fechar esta janela.");
        } catch (Exception e) {
            log.error("Erro ao processar callback do Nubank", e);
            return ResponseEntity.ok("Erro ao processar autorização: " + e.getMessage());
        }
    }

    /**
     * Callback OAuth2 para o Inter
     * 
     * Recebe o código de autorização do Inter e processa a autenticação
     * 
     * @param code Código de autorização retornado pelo Inter
     * @param state Estado para segurança CSRF
     * @param error Erro retornado pelo banco (se houver)
     * @return Redirecionamento para o frontend com resultado
     */
    @GetMapping("/inter/callback")
    @ApiOperation("Callback OAuth2 do Inter")
    public ResponseEntity<String> interCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        
        log.info("Callback OAuth2 recebido do Inter - code: {}, state: {}, error: {}", code, state, error);
        
        if (error != null) {
            log.error("Erro na autorização OAuth2 do Inter: {}", error);
            return ResponseEntity.ok("Erro na autorização: " + error);
        }
        
        if (code == null) {
            log.error("Código de autorização não recebido do Inter");
            return ResponseEntity.badRequest().body("Código de autorização não recebido");
        }
        
        try {
            // Processa a autorização
            Map<String, Object> result = processOAuthCallback(BankApiService.BankType.INTER, code, state);
            return ResponseEntity.ok("Autorização processada com sucesso! Você pode fechar esta janela.");
        } catch (Exception e) {
            log.error("Erro ao processar callback do Inter", e);
            return ResponseEntity.ok("Erro ao processar autorização: " + e.getMessage());
        }
    }

    /**
     * Callback OAuth2 para o Mercado Pago
     * 
     * Recebe o código de autorização do Mercado Pago e processa a autenticação
     * 
     * @param code Código de autorização retornado pelo Mercado Pago
     * @param state Estado para segurança CSRF
     * @param error Erro retornado pelo banco (se houver)
     * @return Redirecionamento para o frontend com resultado
     */
    @GetMapping("/mercadopago/callback")
    @ApiOperation("Callback OAuth2 do Mercado Pago")
    public ResponseEntity<String> mercadoPagoCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        
        log.info("Callback OAuth2 recebido do Mercado Pago - code: {}, state: {}, error: {}", code, state, error);
        
        if (error != null) {
            log.error("Erro na autorização OAuth2 do Mercado Pago: {}", error);
            return ResponseEntity.ok("Erro na autorização: " + error);
        }
        
        if (code == null) {
            log.error("Código de autorização não recebido do Mercado Pago");
            return ResponseEntity.badRequest().body("Código de autorização não recebido");
        }
        
        try {
            // Processa a autorização
            Map<String, Object> result = processOAuthCallback(BankApiService.BankType.MERCADO_PAGO, code, state);
            return ResponseEntity.ok("Autorização processada com sucesso! Você pode fechar esta janela.");
        } catch (Exception e) {
            log.error("Erro ao processar callback do Mercado Pago", e);
            return ResponseEntity.ok("Erro ao processar autorização: " + e.getMessage());
        }
    }

    /**
     * Processa o callback OAuth2 de qualquer banco
     * 
     * @param bankType Tipo do banco
     * @param code Código de autorização
     * @param state Estado para segurança
     * @return Resultado do processamento
     */
    private Map<String, Object> processOAuthCallback(BankApiService.BankType bankType, String code, String state) {
        try {
            // TODO: Extrair userId do state (deve ser criptografado/assinado)
            // Por enquanto, vamos usar um usuário padrão para teste
            Long userId = 1L; // TODO: Implementar extração segura do userId
            
            // Constrói a URI de redirecionamento
            String redirectUri = buildRedirectUri(bankType);
            
            // Troca o código por token
            Map<String, Object> tokenResponse = bankApiService.exchangeCodeForToken(bankType, code, redirectUri);
            
            // Salva a autorização no banco de dados
            autorizacaoBancariaService.salvarAutorizacao(userId, bankType, tokenResponse);
            
            log.info("Autorização OAuth2 processada com sucesso para {} - usuário: {}", bankType, userId);
            
            return tokenResponse;
            
        } catch (Exception e) {
            log.error("Erro ao processar callback OAuth2 para {}", bankType, e);
            throw new RuntimeException("Falha ao processar autorização bancária", e);
        }
    }

    /**
     * Constrói a URI de redirecionamento para cada banco
     * 
     * @param bankType Tipo do banco
     * @return URI de redirecionamento
     */
    private String buildRedirectUri(BankApiService.BankType bankType) {
        String baseUrl = "http://localhost:8080"; // TODO: Configurar via properties
        
        switch (bankType) {
            case ITAU:
                return baseUrl + "/api/bank/oauth/itau/callback";
            case NUBANK:
                return baseUrl + "/api/bank/oauth/nubank/callback";
            case INTER:
                return baseUrl + "/api/bank/oauth/inter/callback";
            case MERCADO_PAGO:
                return baseUrl + "/api/bank/oauth/mercadopago/callback";
            default:
                throw new IllegalArgumentException("Tipo de banco não suportado: " + bankType);
        }
    }
}
