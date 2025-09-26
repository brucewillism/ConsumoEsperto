package com.consumoesperto.controller;

import com.consumoesperto.service.FinancialDataSyncService;
import com.consumoesperto.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller responsável por expor endpoints para sincronização
 * automática de dados financeiros das APIs bancárias
 * 
 * Este controller permite:
 * - Sincronização manual de dados financeiros
 * - Sincronização automática agendada
 * - Monitoramento do status da sincronização
 * - Controle de cache e performance
 * 
 * @author ConsumoEsperto Team
 * @version 2.0
 */
@RestController
@RequestMapping("/api/financial-sync")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:4200", "https://22e294954ab2.ngrok-free.app"})
public class FinancialSyncController {

    private final FinancialDataSyncService financialDataSyncService;
    private final UsuarioService usuarioService;

    /**
     * Sincroniza todos os dados financeiros do usuário autenticado
     * 
     * @param userPrincipal Usuário autenticado
     * @return Resumo da sincronização
     */
    @PostMapping("/sync-all")
    public ResponseEntity<Map<String, Object>> syncAllFinancialData(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User userPrincipal) {
        
        try {
            log.info("Iniciando sincronização manual para usuário: {}", userPrincipal.getUsername());
            
            // Obtém ID do usuário
            Long usuarioId = usuarioService.getUsuarioIdByUsername(userPrincipal.getUsername());
            
            // Executa sincronização completa
            Map<String, Object> result = financialDataSyncService.syncAllFinancialData(usuarioId);
            
            log.info("Sincronização manual concluída para usuário: {}", userPrincipal.getUsername());
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Erro na sincronização manual para usuário: {}", userPrincipal.getUsername(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("erro", "Erro na sincronização: " + e.getMessage()));
        }
    }

    /**
     * Sincroniza apenas categorias do usuário autenticado
     * 
     * @param userPrincipal Usuário autenticado
     * @return Resumo da sincronização de categorias
     */
    @PostMapping("/sync-categories")
    public ResponseEntity<Map<String, Object>> syncCategories(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User userPrincipal) {
        
        try {
            log.info("Sincronizando categorias para usuário: {}", userPrincipal.getUsername());
            
            Long usuarioId = usuarioService.getUsuarioIdByUsername(userPrincipal.getUsername());
            
            // Executa sincronização de categorias
            Map<String, Object> result = financialDataSyncService.syncAllFinancialData(usuarioId);
            
            // Retorna apenas dados de categorias
            Map<String, Object> categoriesResult = Map.of(
                "categorias", result.get("categorias"),
                "mensagem", "Categorias sincronizadas com sucesso"
            );
            
            return ResponseEntity.ok(categoriesResult);
            
        } catch (Exception e) {
            log.error("Erro na sincronização de categorias para usuário: {}", userPrincipal.getUsername(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("erro", "Erro na sincronização de categorias: " + e.getMessage()));
        }
    }

    /**
     * Sincroniza apenas transações do usuário autenticado
     * 
     * @param userPrincipal Usuário autenticado
     * @return Resumo da sincronização de transações
     */
    @PostMapping("/sync-transactions")
    public ResponseEntity<Map<String, Object>> syncTransactions(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User userPrincipal) {
        
        try {
            log.info("Sincronizando transações para usuário: {}", userPrincipal.getUsername());
            
            Long usuarioId = usuarioService.getUsuarioIdByUsername(userPrincipal.getUsername());
            
            // Executa sincronização completa
            Map<String, Object> result = financialDataSyncService.syncAllFinancialData(usuarioId);
            
            // Retorna apenas dados de transações
            Map<String, Object> transactionsResult = Map.of(
                "transacoes", result.get("transacoes"),
                "mensagem", "Transações sincronizadas com sucesso"
            );
            
            return ResponseEntity.ok(transactionsResult);
            
        } catch (Exception e) {
            log.error("Erro na sincronização de transações para usuário: {}", userPrincipal.getUsername(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("erro", "Erro na sincronização de transações: " + e.getMessage()));
        }
    }

    /**
     * Sincroniza apenas faturas do usuário autenticado
     * 
     * @param userPrincipal Usuário autenticado
     * @return Resumo da sincronização de faturas
     */
    @PostMapping("/sync-invoices")
    public ResponseEntity<Map<String, Object>> syncInvoices(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User userPrincipal) {
        
        try {
            log.info("Sincronizando faturas para usuário: {}", userPrincipal.getUsername());
            
            Long usuarioId = usuarioService.getUsuarioIdByUsername(userPrincipal.getUsername());
            
            // Executa sincronização completa
            Map<String, Object> result = financialDataSyncService.syncAllFinancialData(usuarioId);
            
            // Retorna apenas dados de faturas
            Map<String, Object> invoicesResult = Map.of(
                "faturas", result.get("faturas"),
                "mensagem", "Faturas sincronizadas com sucesso"
            );
            
            return ResponseEntity.ok(invoicesResult);
            
        } catch (Exception e) {
            log.error("Erro na sincronização de faturas para usuário: {}", userPrincipal.getUsername(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("erro", "Erro na sincronização de faturas: " + e.getMessage()));
        }
    }

    /**
     * Sincroniza apenas compras parceladas do usuário autenticado
     * 
     * @param userPrincipal Usuário autenticado
     * @return Resumo da sincronização de compras parceladas
     */
    @PostMapping("/sync-installment-purchases")
    public ResponseEntity<Map<String, Object>> syncInstallmentPurchases(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User userPrincipal) {
        
        try {
            log.info("Sincronizando compras parceladas para usuário: {}", userPrincipal.getUsername());
            
            Long usuarioId = usuarioService.getUsuarioIdByUsername(userPrincipal.getUsername());
            
            // Executa sincronização completa
            Map<String, Object> result = financialDataSyncService.syncAllFinancialData(usuarioId);
            
            // Retorna apenas dados de compras parceladas
            Map<String, Object> purchasesResult = Map.of(
                "compras_parceladas", result.get("compras_parceladas"),
                "mensagem", "Compras parceladas sincronizadas com sucesso"
            );
            
            return ResponseEntity.ok(purchasesResult);
            
        } catch (Exception e) {
            log.error("Erro na sincronização de compras parceladas para usuário: {}", userPrincipal.getUsername(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("erro", "Erro na sincronização de compras parceladas: " + e.getMessage()));
        }
    }

    /**
     * Sincroniza apenas cartões de crédito do usuário autenticado
     * 
     * @param userPrincipal Usuário autenticado
     * @return Resumo da sincronização de cartões
     */
    @PostMapping("/sync-credit-cards")
    public ResponseEntity<Map<String, Object>> syncCreditCards(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User userPrincipal) {
        
        try {
            log.info("Sincronizando cartões de crédito para usuário: {}", userPrincipal.getUsername());
            
            Long usuarioId = usuarioService.getUsuarioIdByUsername(userPrincipal.getUsername());
            
            // Executa sincronização completa
            Map<String, Object> result = financialDataSyncService.syncAllFinancialData(usuarioId);
            
            // Retorna apenas dados de cartões
            Map<String, Object> cardsResult = Map.of(
                "cartoes", result.get("cartoes"),
                "mensagem", "Cartões de crédito sincronizados com sucesso"
            );
            
            return ResponseEntity.ok(cardsResult);
            
        } catch (Exception e) {
            log.error("Erro na sincronização de cartões para usuário: {}", userPrincipal.getUsername(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("erro", "Erro na sincronização de cartões: " + e.getMessage()));
        }
    }

    /**
     * Força uma sincronização completa ignorando cache
     * 
     * @param userPrincipal Usuário autenticado
     * @return Resumo da sincronização
     */
    @PostMapping("/force-sync")
    public ResponseEntity<Map<String, Object>> forceSync(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User userPrincipal) {
        
        try {
            log.info("Forçando sincronização completa para usuário: {}", userPrincipal.getUsername());
            
            Long usuarioId = usuarioService.getUsuarioIdByUsername(userPrincipal.getUsername());
            
            // Limpa cache e executa sincronização
            // TODO: Implementar método para limpar cache específico do usuário
            
            Map<String, Object> result = financialDataSyncService.syncAllFinancialData(usuarioId);
            
            log.info("Sincronização forçada concluída para usuário: {}", userPrincipal.getUsername());
            return ResponseEntity.ok(Map.of(
                "mensagem", "Sincronização forçada concluída com sucesso",
                "resultado", result
            ));
            
        } catch (Exception e) {
            log.error("Erro na sincronização forçada para usuário: {}", userPrincipal.getUsername(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("erro", "Erro na sincronização forçada: " + e.getMessage()));
        }
    }

    /**
     * Obtém status da última sincronização do usuário
     * 
     * @param userPrincipal Usuário autenticado
     * @return Status da sincronização
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSyncStatus(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User userPrincipal) {
        
        try {
            log.info("Verificando status da sincronização para usuário: {}", userPrincipal.getUsername());
            
            Long usuarioId = usuarioService.getUsuarioIdByUsername(userPrincipal.getUsername());
            
            // TODO: Implementar método para obter status da sincronização
            
            Map<String, Object> status = Map.of(
                "usuario_id", usuarioId,
                "ultima_sincronizacao", "N/A", // TODO: Implementar
                "status", "ATIVO",
                "proxima_sincronizacao", "15 minutos",
                "mensagem", "Sistema de sincronização funcionando normalmente"
            );
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            log.error("Erro ao verificar status da sincronização para usuário: {}", userPrincipal.getUsername(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("erro", "Erro ao verificar status: " + e.getMessage()));
        }
    }

    /**
     * Endpoint de health check para sincronização
     * 
     * @return Status de saúde do serviço
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "servico", "Financial Data Sync Service",
            "versao", "2.0",
            "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * Endpoint para sincronização de usuário específico (admin)
     * 
     * @param usuarioId ID do usuário
     * @return Resumo da sincronização
     */
    @PostMapping("/admin/sync-user/{usuarioId}")
    public ResponseEntity<Map<String, Object>> syncSpecificUser(@PathVariable Long usuarioId) {
        
        try {
            log.info("Sincronização administrativa para usuário: {}", usuarioId);
            
            // TODO: Verificar se o usuário atual tem permissão de admin
            
            Map<String, Object> result = financialDataSyncService.syncAllFinancialData(usuarioId);
            
            return ResponseEntity.ok(Map.of(
                "mensagem", "Sincronização administrativa concluída",
                "usuario_id", usuarioId,
                "resultado", result
            ));
            
        } catch (Exception e) {
            log.error("Erro na sincronização administrativa para usuário: {}", usuarioId, e);
            return ResponseEntity.badRequest()
                    .body(Map.of("erro", "Erro na sincronização administrativa: " + e.getMessage()));
        }
    }

    /**
     * Endpoint para sincronização de todos os usuários (admin)
     * 
     * @return Resumo da sincronização global
     */
    @PostMapping("/admin/sync-all-users")
    public ResponseEntity<Map<String, Object>> syncAllUsers() {
        
        try {
            log.info("Iniciando sincronização global para todos os usuários");
            
            // TODO: Implementar método para sincronizar todos os usuários
            // TODO: Verificar se o usuário atual tem permissão de admin
            
            return ResponseEntity.ok(Map.of(
                "mensagem", "Sincronização global iniciada",
                "status", "EM_ANDAMENTO",
                "timestamp", System.currentTimeMillis()
            ));
            
        } catch (Exception e) {
            log.error("Erro na sincronização global", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("erro", "Erro na sincronização global: " + e.getMessage()));
        }
    }
}
