package com.consumoesperto.service;

import com.consumoesperto.model.BankApiConfig;
import com.consumoesperto.repository.BankApiConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Serviço para carregar configurações das APIs bancárias dinamicamente por usuário
 * Permite que as configurações sejam alteradas em tempo real sem reiniciar a aplicação
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicBankConfigService {

    private final BankApiConfigRepository configRepository;

    // Cache das configurações em memória por usuário
    private final Map<Long, Map<String, BankApiConfig>> userConfigCache = new HashMap<>();

    @PostConstruct
    public void initialize() {
        log.info("Inicializando serviço de configuração dinâmica das APIs bancárias");
        // Não carrega todas as configurações no início, apenas quando solicitado
    }

    /**
     * Carrega todas as configurações de um usuário específico
     */
    public void loadUserConfigs(Long usuarioId) {
        try {
            Map<String, BankApiConfig> userConfigs = new HashMap<>();
            configRepository.findByUsuarioId(usuarioId).forEach(config -> {
                userConfigs.put(config.getBankCode(), config);
                log.info("Configuração carregada para usuário {} - banco {}: {}", usuarioId, config.getBankName(), config.getBankCode());
            });
            userConfigCache.put(usuarioId, userConfigs);
            log.info("Total de {} configurações carregadas para usuário {}", userConfigs.size(), usuarioId);
        } catch (Exception e) {
            log.error("Erro ao carregar configurações do usuário {}: {}", usuarioId, e.getMessage(), e);
        }
    }

    /**
     * Carrega todas as configurações do banco de dados (método legado para compatibilidade)
     */
    public void loadAllConfigs() {
        try {
            userConfigCache.clear();
            // Agrupa configurações por usuário
            configRepository.findAll().forEach(config -> {
                Long usuarioId = config.getUsuario().getId();
                userConfigCache.computeIfAbsent(usuarioId, k -> new HashMap<>())
                    .put(config.getBankCode(), config);
            });
            log.info("Total de {} usuários com configurações carregadas", userConfigCache.size());
        } catch (Exception e) {
            log.error("Erro ao carregar configurações: {}", e.getMessage(), e);
        }
    }

    /**
     * Busca configuração por código do banco para um usuário específico
     */
    public Optional<BankApiConfig> getConfig(Long usuarioId, String bankCode) {
        // Carrega configurações do usuário se não estiverem em cache
        if (!userConfigCache.containsKey(usuarioId)) {
            loadUserConfigs(usuarioId);
        }
        
        Map<String, BankApiConfig> userConfigs = userConfigCache.get(usuarioId);
        if (userConfigs != null) {
            return Optional.ofNullable(userConfigs.get(bankCode));
        }
        return Optional.empty();
    }

    /**
     * Busca configuração por código do banco (método legado para compatibilidade)
     */
    public Optional<BankApiConfig> getConfig(String bankCode) {
        // Para compatibilidade, busca em todas as configurações
        return configRepository.findByBankCode(bankCode);
    }

    /**
     * Busca configuração por código do banco (com fallback para propriedades)
     */
    public Optional<BankApiConfig> getConfigWithFallback(Long usuarioId, String bankCode) {
        Optional<BankApiConfig> config = getConfig(usuarioId, bankCode);
        if (config.isPresent()) {
            return config;
        }

        // Fallback para propriedades estáticas
        log.warn("Configuração não encontrada para usuário {} - banco {}, usando propriedades estáticas", usuarioId, bankCode);
        return Optional.empty();
    }

    /**
     * Busca configuração por código do banco (com fallback para propriedades) - método legado
     */
    public Optional<BankApiConfig> getConfigWithFallback(String bankCode) {
        Optional<BankApiConfig> config = getConfig(bankCode);
        if (config.isPresent()) {
            return config;
        }

        // Fallback para propriedades estáticas
        log.warn("Configuração não encontrada para {}, usando propriedades estáticas", bankCode);
        return Optional.empty();
    }

    /**
     * Atualiza configuração no cache de um usuário específico
     */
    public void updateConfig(BankApiConfig config) {
        if (config != null && config.getUsuario() != null && config.getBankCode() != null) {
            Long usuarioId = config.getUsuario().getId();
            userConfigCache.computeIfAbsent(usuarioId, k -> new HashMap<>())
                .put(config.getBankCode(), config);
            log.info("Configuração atualizada no cache: usuário {} - banco {}", usuarioId, config.getBankCode());
        }
    }

    /**
     * Remove configuração do cache de um usuário específico
     */
    public void removeConfig(Long usuarioId, String bankCode) {
        Map<String, BankApiConfig> userConfigs = userConfigCache.get(usuarioId);
        if (userConfigs != null) {
            userConfigs.remove(bankCode);
            log.info("Configuração removida do cache: usuário {} - banco {}", usuarioId, bankCode);
        }
    }

    /**
     * Remove configuração do cache (método legado para compatibilidade)
     */
    public void removeConfig(String bankCode) {
        // Remove de todos os usuários
        userConfigCache.values().forEach(userConfigs -> userConfigs.remove(bankCode));
        log.info("Configuração removida do cache: banco {}", bankCode);
    }

    /**
     * Recarrega configuração específica de um usuário
     */
    public void reloadUserConfig(Long usuarioId, String bankCode) {
        try {
            Optional<BankApiConfig> config = configRepository.findByUsuarioIdAndBankCode(usuarioId, bankCode);
            if (config.isPresent()) {
                userConfigCache.computeIfAbsent(usuarioId, k -> new HashMap<>())
                    .put(bankCode, config.get());
                log.info("Configuração recarregada: usuário {} - banco {}", usuarioId, bankCode);
            } else {
                removeConfig(usuarioId, bankCode);
                log.info("Configuração removida do cache: usuário {} - banco {}", usuarioId, bankCode);
            }
        } catch (Exception e) {
            log.error("Erro ao recarregar configuração usuário {} - banco {}: {}", usuarioId, bankCode, e.getMessage(), e);
        }
    }

    /**
     * Recarrega configuração específica (método legado para compatibilidade)
     */
    public void reloadConfig(String bankCode) {
        try {
            Optional<BankApiConfig> config = configRepository.findByBankCode(bankCode);
            if (config.isPresent()) {
                // Atualiza em todos os usuários que têm esta configuração
                configRepository.findAll().stream()
                    .filter(c -> c.getBankCode().equals(bankCode))
                    .forEach(this::updateConfig);
                log.info("Configuração recarregada: {}", bankCode);
            } else {
                removeConfig(bankCode);
                log.info("Configuração removida do cache: {}", bankCode);
            }
        } catch (Exception e) {
            log.error("Erro ao recarregar configuração {}: {}", bankCode, e.getMessage(), e);
        }
    }

    /**
     * Verifica se uma configuração está ativa para um usuário específico
     */
    public boolean isConfigActive(Long usuarioId, String bankCode) {
        return getConfig(usuarioId, bankCode)
                .map(BankApiConfig::getIsActive)
                .orElse(false);
    }

    /**
     * Verifica se uma configuração está ativa (método legado para compatibilidade)
     */
    public boolean isConfigActive(String bankCode) {
        return getConfig(bankCode)
                .map(BankApiConfig::getIsActive)
                .orElse(false);
    }

    /**
     * Verifica se uma configuração está em modo sandbox para um usuário específico
     */
    public boolean isConfigSandbox(Long usuarioId, String bankCode) {
        return getConfig(usuarioId, bankCode)
                .map(BankApiConfig::getIsSandbox)
                .orElse(true);
    }

    /**
     * Verifica se uma configuração está em modo sandbox (método legado para compatibilidade)
     */
    public boolean isConfigSandbox(String bankCode) {
        return getConfig(bankCode)
                .map(BankApiConfig::getIsSandbox)
                .orElse(true);
    }

    /**
     * Obtém timeout de uma configuração para um usuário específico
     */
    public int getConfigTimeout(Long usuarioId, String bankCode) {
        return getConfig(usuarioId, bankCode)
                .map(BankApiConfig::getTimeoutMs)
                .orElse(30000);
    }

    /**
     * Obtém timeout de uma configuração (método legado para compatibilidade)
     */
    public int getConfigTimeout(String bankCode) {
        return getConfig(bankCode)
                .map(BankApiConfig::getTimeoutMs)
                .orElse(30000);
    }

    /**
     * Obtém número máximo de tentativas de uma configuração para um usuário específico
     */
    public int getConfigMaxRetries(Long usuarioId, String bankCode) {
        return getConfig(usuarioId, bankCode)
                .map(BankApiConfig::getMaxRetries)
                .orElse(3);
    }

    /**
     * Obtém número máximo de tentativas de uma configuração (método legado para compatibilidade)
     */
    public int getConfigMaxRetries(String bankCode) {
        return getConfig(bankCode)
                .map(BankApiConfig::getMaxRetries)
                .orElse(3);
    }

    /**
     * Obtém delay entre tentativas de uma configuração para um usuário específico
     */
    public int getConfigRetryDelay(Long usuarioId, String bankCode) {
        return getConfig(usuarioId, bankCode)
                .map(BankApiConfig::getRetryDelayMs)
                .orElse(1000);
    }

    /**
     * Obtém delay entre tentativas de uma configuração (método legado para compatibilidade)
     */
    public int getConfigRetryDelay(String bankCode) {
        return getConfig(bankCode)
                .map(BankApiConfig::getRetryDelayMs)
                .orElse(1000);
    }

    /**
     * Lista todos os códigos de banco configurados para um usuário específico
     */
    public java.util.Set<String> getConfiguredBanksForUser(Long usuarioId) {
        if (!userConfigCache.containsKey(usuarioId)) {
            loadUserConfigs(usuarioId);
        }
        Map<String, BankApiConfig> userConfigs = userConfigCache.get(usuarioId);
        return userConfigs != null ? userConfigs.keySet() : java.util.Collections.emptySet();
    }

    /**
     * Lista todos os códigos de banco configurados (método legado para compatibilidade)
     */
    public java.util.Set<String> getConfiguredBanks() {
        return configRepository.findAll().stream()
                .map(BankApiConfig::getBankCode)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Lista todas as configurações ativas de um usuário específico
     */
    public java.util.List<BankApiConfig> getActiveConfigsForUser(Long usuarioId) {
        if (!userConfigCache.containsKey(usuarioId)) {
            loadUserConfigs(usuarioId);
        }
        Map<String, BankApiConfig> userConfigs = userConfigCache.get(usuarioId);
        if (userConfigs != null) {
            return userConfigs.values().stream()
                    .filter(BankApiConfig::getIsActive)
                    .collect(java.util.stream.Collectors.toList());
        }
        return java.util.Collections.emptyList();
    }

    /**
     * Lista todas as configurações ativas (método legado para compatibilidade)
     */
    public java.util.List<BankApiConfig> getActiveConfigs() {
        return configRepository.findByIsActiveTrue();
    }

    /**
     * Verifica se há configurações carregadas para um usuário específico
     */
    public boolean hasConfigsForUser(Long usuarioId) {
        if (!userConfigCache.containsKey(usuarioId)) {
            loadUserConfigs(usuarioId);
        }
        Map<String, BankApiConfig> userConfigs = userConfigCache.get(usuarioId);
        return userConfigs != null && !userConfigs.isEmpty();
    }

    /**
     * Verifica se há configurações carregadas (método legado para compatibilidade)
     */
    public boolean hasConfigs() {
        return !userConfigCache.isEmpty();
    }

    /**
     * Obtém estatísticas das configurações de um usuário específico
     */
    public Map<String, Object> getConfigStatsForUser(Long usuarioId) {
        if (!userConfigCache.containsKey(usuarioId)) {
            loadUserConfigs(usuarioId);
        }
        
        Map<String, BankApiConfig> userConfigs = userConfigCache.get(usuarioId);
        if (userConfigs == null) {
            return createEmptyStats();
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", userConfigs.size());
        stats.put("active", userConfigs.values().stream().filter(BankApiConfig::getIsActive).count());
        stats.put("sandbox", userConfigs.values().stream().filter(BankApiConfig::getIsSandbox).count());
        stats.put("production", userConfigs.values().stream().filter(c -> !c.getIsSandbox()).count());

        // Status dos testes
        long successTests = userConfigs.values().stream()
                .filter(c -> "SUCCESS".equals(c.getLastTestStatus()))
                .count();
        long failedTests = userConfigs.values().stream()
                .filter(c -> "FAILED".equals(c.getLastTestStatus()))
                .count();
        long notTested = userConfigs.values().stream()
                .filter(c -> "NOT_TESTED".equals(c.getLastTestStatus()) || c.getLastTestStatus() == null)
                .count();

        stats.put("testsSuccess", successTests);
        stats.put("testsFailed", failedTests);
        stats.put("testsNotTested", notTested);

        return stats;
    }

    /**
     * Obtém estatísticas das configurações (método legado para compatibilidade)
     */
    public Map<String, Object> getConfigStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", userConfigCache.values().stream().mapToInt(Map::size).sum());
        stats.put("active", userConfigCache.values().stream()
                .flatMap(userConfigs -> userConfigs.values().stream())
                .filter(BankApiConfig::getIsActive)
                .count());
        stats.put("sandbox", userConfigCache.values().stream()
                .flatMap(userConfigs -> userConfigs.values().stream())
                .filter(BankApiConfig::getIsSandbox)
                .count());
        stats.put("production", userConfigCache.values().stream()
                .flatMap(userConfigs -> userConfigs.values().stream())
                .filter(c -> !c.getIsSandbox())
                .count());

        // Status dos testes
        long successTests = userConfigCache.values().stream()
                .flatMap(userConfigs -> userConfigs.values().stream())
                .filter(c -> "SUCCESS".equals(c.getLastTestStatus()))
                .count();
        long failedTests = userConfigCache.values().stream()
                .flatMap(userConfigs -> userConfigs.values().stream())
                .filter(c -> "FAILED".equals(c.getLastTestStatus()))
                .count();
        long notTested = userConfigCache.values().stream()
                .flatMap(userConfigs -> userConfigs.values().stream())
                .filter(c -> "NOT_TESTED".equals(c.getLastTestStatus()) || c.getLastTestStatus() == null)
                .count();

        stats.put("testsSuccess", successTests);
        stats.put("testsFailed", failedTests);
        stats.put("testsNotTested", notTested);

        return stats;
    }

    private Map<String, Object> createEmptyStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", 0);
        stats.put("active", 0);
        stats.put("sandbox", 0);
        stats.put("production", 0);
        stats.put("testsSuccess", 0);
        stats.put("testsFailed", 0);
        stats.put("testsNotTested", 0);
        return stats;
    }
}
