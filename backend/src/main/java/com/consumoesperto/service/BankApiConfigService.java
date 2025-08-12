package com.consumoesperto.service;

import com.consumoesperto.model.BankApiConfig;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.BankApiConfigRepository;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Serviço para gerenciar configurações das APIs bancárias por usuário
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BankApiConfigService {

    private final BankApiConfigRepository configRepository;
    private final UsuarioRepository usuarioRepository;

    /**
     * Cria ou atualiza configuração de API bancária para um usuário específico
     */
    @Transactional
    public BankApiConfig saveConfig(BankApiConfig config) {
        log.info("Salvando configuração para banco: {} - Usuário: {}", config.getBankName(), config.getUsuario().getId());

        // Se é uma atualização, preserva alguns campos
        if (config.getId() != null) {
            Optional<BankApiConfig> existing = configRepository.findById(config.getId());
            if (existing.isPresent()) {
                BankApiConfig existingConfig = existing.get();
                config.setCreatedAt(existingConfig.getCreatedAt());
                config.setLastTestAt(existingConfig.getLastTestAt());
                config.setLastTestStatus(existingConfig.getLastTestStatus());
                config.setLastTestMessage(existingConfig.getLastTestMessage());
            }
        }

        BankApiConfig saved = configRepository.save(config);
        log.info("Configuração salva com sucesso. ID: {}", saved.getId());
        return saved;
    }

    /**
     * Busca configuração por código do banco para um usuário específico
     */
    public Optional<BankApiConfig> findByUsuarioIdAndBankCode(Long usuarioId, String bankCode) {
        return configRepository.findByUsuarioIdAndBankCode(usuarioId, bankCode);
    }

    /**
     * Busca configuração por código do banco (método legado para compatibilidade)
     */
    public Optional<BankApiConfig> findByBankCode(String bankCode) {
        return configRepository.findByBankCode(bankCode);
    }

    /**
     * Busca configuração por nome do banco (método legado para compatibilidade)
     */
    public Optional<BankApiConfig> findByBankName(String bankName) {
        return configRepository.findByBankName(bankName);
    }

    /**
     * Lista todas as configurações de um usuário
     */
    public List<BankApiConfig> findByUsuarioId(Long usuarioId) {
        return configRepository.findByUsuarioId(usuarioId);
    }

    /**
     * Lista todas as configurações (método legado para compatibilidade)
     */
    public List<BankApiConfig> findAll() {
        return configRepository.findAll();
    }

    /**
     * Lista configurações ativas de um usuário
     */
    public List<BankApiConfig> findActiveConfigsByUsuario(Long usuarioId) {
        return configRepository.findByUsuarioIdAndIsActiveTrue(usuarioId);
    }

    /**
     * Lista configurações ativas (método legado para compatibilidade)
     */
    public List<BankApiConfig> findActiveConfigs() {
        return configRepository.findByIsActiveTrue();
    }

    /**
     * Atualiza status de teste de uma configuração
     */
    @Transactional
    public void updateTestStatus(Long configId, String status, String message) {
        Optional<BankApiConfig> configOpt = configRepository.findById(configId);
        if (configOpt.isPresent()) {
            BankApiConfig config = configOpt.get();
            config.setLastTestAt(LocalDateTime.now());
            config.setLastTestStatus(status);
            config.setLastTestMessage(message);
            configRepository.save(config);
            log.info("Status de teste atualizado para configuração {}: {}", configId, status);
        }
    }

    /**
     * Ativa/desativa uma configuração
     */
    @Transactional
    public void toggleActiveStatus(Long configId) {
        Optional<BankApiConfig> configOpt = configRepository.findById(configId);
        if (configOpt.isPresent()) {
            BankApiConfig config = configOpt.get();
            config.setIsActive(!config.getIsActive());
            configRepository.save(config);
            log.info("Status ativo alterado para configuração {}: {}", configId, config.getIsActive());
        }
    }

    /**
     * Remove uma configuração
     */
    @Transactional
    public void deleteConfig(Long configId) {
        if (configRepository.existsById(configId)) {
            configRepository.deleteById(configId);
            log.info("Configuração removida. ID: {}", configId);
        }
    }

    /**
     * Cria configurações padrão para um usuário específico
     */
    @Transactional
    public void createDefaultConfigsForUser(Long usuarioId) {
        log.info("Criando configurações padrão para usuário: {}", usuarioId);
        
        Optional<Usuario> usuarioOpt = usuarioRepository.findById(usuarioId);
        if (!usuarioOpt.isPresent()) {
            log.error("Usuário não encontrado: {}", usuarioId);
            return;
        }
        
        Usuario usuario = usuarioOpt.get();

        // Mercado Pago
        if (!configRepository.existsByUsuarioIdAndBankCode(usuarioId, "MERCADOPAGO")) {
            BankApiConfig mercadopago = BankApiConfig.builder()
                    .bankName("Mercado Pago")
                    .bankCode("MERCADOPAGO")
                    .usuario(usuario)
                    .clientId("4223603750190943")
                    .clientSecret("APP_USR-4223603750190943-XXXXXX")
                    .userId("209112973")
                    .apiUrl("https://api.mercadopago.com/v1")
                    .authUrl("https://api.mercadopago.com/authorization")
                    .tokenUrl("https://api.mercadopago.com/oauth/token")
                    .redirectUri("https://29e1b0b32eb8.ngrok-free.app/api/auth/mercadopago/callback")
                    .scope("read,write")
                    .isSandbox(true)
                    .isActive(true)
                    .timeoutMs(30000)
                    .maxRetries(3)
                    .retryDelayMs(1000)
                    .build();
            configRepository.save(mercadopago);
        }

        // Itaú
        if (!configRepository.existsByUsuarioIdAndBankCode(usuarioId, "ITAU")) {
            BankApiConfig itau = BankApiConfig.builder()
                    .bankName("Itaú")
                    .bankCode("ITAU")
                    .usuario(usuario)
                    .clientId("your_itau_client_id")
                    .clientSecret("your_itau_client_secret")
                    .apiUrl("https://openbanking.itau.com.br/api")
                    .authUrl("https://openbanking.itau.com.br/oauth/authorize")
                    .tokenUrl("https://openbanking.itau.com.br/oauth/token")
                    .redirectUri("https://29e1b0b32eb8.ngrok-free.app/api/auth/itau/callback")
                    .scope("openid,profile,email,accounts,transactions")
                    .isSandbox(true)
                    .isActive(true)
                    .timeoutMs(30000)
                    .maxRetries(3)
                    .retryDelayMs(1000)
                    .build();
            configRepository.save(itau);
        }

        // Inter
        if (!configRepository.existsByUsuarioIdAndBankCode(usuarioId, "INTER")) {
            BankApiConfig inter = BankApiConfig.builder()
                    .bankName("Inter")
                    .bankCode("INTER")
                    .usuario(usuario)
                    .clientId("your_inter_client_id")
                    .clientSecret("your_inter_client_secret")
                    .apiUrl("https://cdp.openbanking.bancointer.com.br/api")
                    .authUrl("https://cdp.openbanking.bancointer.com.br/oauth/authorize")
                    .tokenUrl("https://cdp.openbanking.bancointer.com.br/oauth/token")
                    .redirectUri("https://29e1b0b32eb8.ngrok-free.app/api/auth/inter/callback")
                    .scope("openid,profile,email,accounts,transactions")
                    .isSandbox(true)
                    .isActive(true)
                    .timeoutMs(30000)
                    .maxRetries(3)
                    .retryDelayMs(1000)
                    .build();
            configRepository.save(inter);
        }

        // Nubank
        if (!configRepository.existsByUsuarioIdAndBankCode(usuarioId, "NUBANK")) {
            BankApiConfig nubank = BankApiConfig.builder()
                    .bankName("Nubank")
                    .bankCode("NUBANK")
                    .usuario(usuario)
                    .clientId("your_nubank_client_id")
                    .clientSecret("your_nubank_client_secret")
                    .apiUrl("https://api.nubank.com.br/api")
                    .authUrl("https://api.nubank.com.br/oauth/authorize")
                    .tokenUrl("https://api.nubank.com.br/oauth/token")
                    .redirectUri("https://29e1b0b32eb8.ngrok-free.app/api/auth/nubank/callback")
                    .scope("openid,profile,email,accounts,transactions")
                    .isSandbox(true)
                    .isActive(true)
                    .timeoutMs(30000)
                    .maxRetries(3)
                    .retryDelayMs(1000)
                    .build();
            configRepository.save(nubank);
        }

        log.info("Configurações padrão criadas com sucesso para usuário: {}", usuarioId);
    }

    /**
     * Cria configurações padrão para todos os bancos (método legado para compatibilidade)
     */
    @Transactional
    public void createDefaultConfigs() {
        log.info("Criando configurações padrão para APIs bancárias");
        // Este método agora cria configurações para um usuário padrão ou admin
        // Por enquanto, vamos deixar vazio para evitar conflitos
        log.warn("Método createDefaultConfigs() foi descontinuado. Use createDefaultConfigsForUser()");
    }
}
