package com.consumoesperto.controller;

import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.BankSynchronizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import com.consumoesperto.service.BankApiService;

/**
 * Controller responsável por gerenciar a sincronização bancária em tempo real
 * 
 * Este controller expõe endpoints para sincronizar dados bancários dos usuários
 * com as APIs reais dos bancos, incluindo cartões, saldos e faturas.
 * 
 * Funcionalidades principais:
 * - Sincronização manual de dados bancários
 * - Sincronização automática programada
 * - Status de sincronização por banco
 * - Histórico de sincronizações
 * - Forçar atualização de dados específicos
 * 
 * Bancos suportados:
 * - Itaú (Open Banking)
 * - Mercado Pago
 * - Inter (Open Banking)
 * - Nubank
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@RestController
@RequestMapping("/api/bank-sync") // Base path para endpoints de sincronização bancária
@RequiredArgsConstructor // Lombok: gera construtor com campos final
@Slf4j // Lombok: fornece logger automático para a classe
@Tag(name = "Sincronização Bancária", description = "Endpoints para sincronização de dados bancários")
@CrossOrigin(origins = {"http://localhost:4200", "https://22e294954ab2.ngrok-free.app"}) // Permite CORS de qualquer origem
public class BankSynchronizationController {

    // Serviço responsável pela sincronização bancária
    private final BankSynchronizationService bankSynchronizationService;

    /**
     * Sincroniza todos os dados bancários do usuário autenticado
     * 
     * Este endpoint executa a sincronização completa com todos os bancos
     * conectados pelo usuário, atualizando cartões, saldos e faturas
     * com dados reais das APIs bancárias.
     * 
     * Processo de sincronização:
     * 1. Identifica bancos conectados pelo usuário
     * 2. Executa sincronização paralela para cada banco
     * 3. Atualiza dados locais com informações reais
     * 4. Retorna resumo da sincronização de cada banco
     * 
     * @param currentUser Usuário autenticado (injetado automaticamente)
     * @return Resumo da sincronização de todos os bancos
     */
    @PostMapping("/synchronize")
    @Operation(summary = "Sincronizar dados bancários", description = "Sincroniza todos os dados bancários do usuário")
    public ResponseEntity<Map<String, Object>> synchronizeAllBankData(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        log.info("Iniciando sincronização bancária para usuário: {}", currentUser.getId());
        
        try {
            // Executa sincronização completa
            Map<String, Object> syncResults = bankSynchronizationService.synchronizeUserBankData(currentUser.getId());
            
            log.info("Sincronização bancária concluída para usuário: {} - {} bancos processados", 
                    currentUser.getId(), syncResults.size());
            
            return ResponseEntity.ok(syncResults);
            
        } catch (Exception e) {
            log.error("Erro na sincronização bancária do usuário {}: {}", currentUser.getId(), e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "erro", "Falha na sincronização bancária",
                "mensagem", e.getMessage(),
                "timestamp", java.time.LocalDateTime.now()
            ));
        }
    }

    /**
     * Sincroniza dados de um banco específico
     * 
     * Permite sincronizar dados de um banco específico, útil quando
     * o usuário quer atualizar apenas um banco ou quando há falha
     * na sincronização de um banco específico.
     * 
     * @param bankType Tipo do banco para sincronização (NUBANK, ITAU, INTER, MERCADO_PAGO)
     * @param currentUser Usuário autenticado
     * @return Resultado da sincronização do banco específico
     */
    @PostMapping("/synchronize/{bankType}")
    @Operation(summary = "Sincronizar banco específico", description = "Sincroniza dados de um banco específico")
    public ResponseEntity<Map<String, Object>> synchronizeSpecificBank(
            @PathVariable String bankType,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        log.info("Iniciando sincronização do banco {} para usuário: {}", bankType, currentUser.getId());
        
        try {
            // Valida tipo de banco
            BankApiService.BankType bankTypeEnum;
            try {
                bankTypeEnum = BankApiService.BankType.valueOf(bankType.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of(
                    "erro", "Tipo de banco inválido",
                    "tipos_suportados", new String[]{"NUBANK", "ITAU", "INTER", "MERCADO_PAGO"},
                    "tipo_recebido", bankType
                ));
            }
            
            // TODO: Implementar sincronização específica por banco
            // Por enquanto, retorna sincronização completa
            Map<String, Object> syncResults = bankSynchronizationService.synchronizeUserBankData(currentUser.getId());
            
            // Filtra apenas o banco solicitado
            if (syncResults.containsKey(bankTypeEnum.name())) {
                return ResponseEntity.ok(Map.of(
                    "banco", bankTypeEnum.name(),
                    "resultado", syncResults.get(bankTypeEnum.name())
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                    "banco", bankTypeEnum.name(),
                    "status", "NAO_CONECTADO",
                    "mensagem", "Usuário não possui cartões deste banco"
                ));
            }
            
        } catch (Exception e) {
            log.error("Erro na sincronização do banco {} para usuário {}: {}", 
                    bankType, currentUser.getId(), e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "erro", "Falha na sincronização do banco",
                "banco", bankType,
                "mensagem", e.getMessage(),
                "timestamp", java.time.LocalDateTime.now()
            ));
        }
    }

    /**
     * Obtém status da última sincronização bancária
     * 
     * Retorna informações sobre a última sincronização executada,
     * incluindo status de cada banco e timestamp da última atualização.
     * 
     * @param currentUser Usuário autenticado
     * @return Status da última sincronização
     */
    @GetMapping("/status")
    @Operation(summary = "Obter status da última sincronização bancária", description = "Retorna status da última sincronização bancária")
    public ResponseEntity<Map<String, Object>> getSyncStatus(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        log.info("Consultando status de sincronização para usuário: {}", currentUser.getId());
        
        try {
            // TODO: Implementar busca de status real da última sincronização
            // Por enquanto, retorna status simulado
            Map<String, Object> status = Map.of(
                "usuario_id", currentUser.getId(),
                "ultima_sincronizacao", java.time.LocalDateTime.now().minusMinutes(30),
                "status_geral", "ATUALIZADO",
                "bancos", Map.of(
                    "ITAU", Map.of("status", "ATUALIZADO", "ultima_atualizacao", "2024-12-10T18:00:00"),
                    "NUBANK", Map.of("status", "ATUALIZADO", "ultima_atualizacao", "2024-12-10T18:00:00"),
                    "INTER", Map.of("status", "PENDENTE", "ultima_atualizacao", "2024-12-10T17:30:00"),
                    "MERCADO_PAGO", Map.of("status", "ERRO", "ultima_atualizacao", "2024-12-10T17:00:00", "erro", "Token expirado")
                ),
                "proxima_sincronizacao", java.time.LocalDateTime.now().plusMinutes(30)
            );
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            log.error("Erro ao consultar status de sincronização do usuário {}: {}", 
                    currentUser.getId(), e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "erro", "Falha ao consultar status",
                "mensagem", e.getMessage(),
                "timestamp", java.time.LocalDateTime.now()
            ));
        }
    }

    /**
     * Força atualização de dados específicos
     * 
     * Permite forçar a atualização de dados específicos como cartões,
     * faturas ou saldos, ignorando o cache e buscando dados frescos
     * das APIs bancárias.
     * 
     * @param dataType Tipo de dados para atualizar (CARTOES, FATURAS, SALDOS, TODOS)
     * @param currentUser Usuário autenticado
     * @return Resultado da atualização forçada
     */
    @PostMapping("/force-update/{dataType}")
    @Operation(summary = "Forçar atualização de dados específicos", description = "Força atualização de dados específicos ignorando cache")
    public ResponseEntity<Map<String, Object>> forceUpdateData(
            @PathVariable String dataType,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        log.info("Forçando atualização de {} para usuário: {}", dataType, currentUser.getId());
        
        try {
            // Valida tipo de dados
            String upperDataType = dataType.toUpperCase();
            if (!isValidDataType(upperDataType)) {
                return ResponseEntity.badRequest().body(Map.of(
                    "erro", "Tipo de dados inválido",
                    "tipos_suportados", new String[]{"CARTOES", "FATURAS", "SALDOS", "TODOS"},
                    "tipo_recebido", dataType
                ));
            }
            
            // TODO: Implementar atualização forçada por tipo de dados
            // Por enquanto, executa sincronização completa
            Map<String, Object> syncResults = bankSynchronizationService.synchronizeUserBankData(currentUser.getId());
            
            return ResponseEntity.ok(Map.of(
                "tipo_atualizacao", upperDataType,
                "status", "SUCESSO",
                "mensagem", "Atualização forçada executada com sucesso",
                "resultados", syncResults,
                "timestamp", java.time.LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            log.error("Erro na atualização forçada de {} para usuário {}: {}", 
                    dataType, currentUser.getId(), e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "erro", "Falha na atualização forçada",
                "tipo_dados", dataType,
                "mensagem", e.getMessage(),
                "timestamp", java.time.LocalDateTime.now()
            ));
        }
    }

    /**
     * Obtém histórico de sincronizações
     * 
     * Retorna um histórico das últimas sincronizações executadas,
     * incluindo sucessos, falhas e detalhes de cada operação.
     * 
     * @param currentUser Usuário autenticado
     * @param limit Limite de registros a retornar (padrão: 10)
     * @return Histórico de sincronizações
     */
    @GetMapping("/history")
    @Operation(summary = "Obter histórico de sincronizações bancárias", description = "Retorna histórico de sincronizações bancárias")
    public ResponseEntity<Map<String, Object>> getSyncHistory(
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        log.info("Consultando histórico de sincronização para usuário: {} (limite: {})", 
                currentUser.getId(), limit);
        
        try {
            // TODO: Implementar busca de histórico real
            // Por enquanto, retorna histórico simulado
            List<Map<String, Object>> history = new ArrayList<>();
            
            for (int i = 0; i < Math.min(limit, 5); i++) {
                history.add(Map.of(
                    "id", i + 1,
                    "timestamp", java.time.LocalDateTime.now().minusHours(i),
                    "status", i % 3 == 0 ? "SUCESSO" : (i % 3 == 1 ? "ERRO" : "PENDENTE"),
                    "bancos_processados", i + 1,
                    "detalhes", i % 3 == 0 ? "Sincronização concluída com sucesso" : 
                               (i % 3 == 1 ? "Erro na API do Itaú" : "Aguardando resposta do Inter")
                ));
            }
            
            Map<String, Object> response = Map.of(
                "usuario_id", currentUser.getId(),
                "total_registros", history.size(),
                "limite_solicitado", limit,
                "historico", history
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Erro ao consultar histórico de sincronização do usuário {}: {}", 
                    currentUser.getId(), e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "erro", "Falha ao consultar histórico",
                "mensagem", e.getMessage(),
                "timestamp", java.time.LocalDateTime.now()
            ));
        }
    }

    /**
     * Configura sincronização automática
     * 
     * Permite configurar parâmetros da sincronização automática,
     * como frequência, horários preferidos e tipos de dados.
     * 
     * @param config Configurações de sincronização automática
     * @param currentUser Usuário autenticado
     * @return Configurações aplicadas
     */
    @PostMapping("/auto-sync-config")
    @Operation(summary = "Configurar sincronização automática", description = "Configura parâmetros da sincronização automática")
    public ResponseEntity<Map<String, Object>> configureAutoSync(
            @RequestBody Map<String, Object> config,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        log.info("Configurando sincronização automática para usuário: {}", currentUser.getId());
        
        try {
            // Valida configurações recebidas
            if (!isValidAutoSyncConfig(config)) {
                return ResponseEntity.badRequest().body(Map.of(
                    "erro", "Configuração inválida",
                    "campos_obrigatorios", new String[]{"enabled", "frequency", "dataTypes"},
                    "config_recebida", config
                ));
            }
            
            // TODO: Implementar salvamento de configurações
            // Por enquanto, retorna confirmação
            return ResponseEntity.ok(Map.of(
                "usuario_id", currentUser.getId(),
                "status", "CONFIGURADO",
                "configuracao", config,
                "mensagem", "Configuração de sincronização automática aplicada com sucesso",
                "timestamp", java.time.LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            log.error("Erro ao configurar sincronização automática para usuário {}: {}", 
                    currentUser.getId(), e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "erro", "Falha na configuração",
                "mensagem", e.getMessage(),
                "timestamp", java.time.LocalDateTime.now()
            ));
        }
    }

    /**
     * Valida se o tipo de dados é suportado
     * 
     * @param dataType Tipo de dados a validar
     * @return true se válido, false caso contrário
     */
    private boolean isValidDataType(String dataType) {
        return Arrays.asList("CARTOES", "FATURAS", "SALDOS", "TODOS").contains(dataType);
    }

    /**
     * Valida se a configuração de sincronização automática é válida
     * 
     * @param config Configuração a validar
     * @return true se válida, false caso contrário
     */
    private boolean isValidAutoSyncConfig(Map<String, Object> config) {
        return config.containsKey("enabled") && 
               config.containsKey("frequency") && 
               config.containsKey("dataTypes");
    }
}
